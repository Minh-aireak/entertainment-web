package com.MyProject.comment_service.service;

import com.MyProject.comment_service.dto.event.CommentReactionChangedEvent;
import com.MyProject.comment_service.dto.response.CommentReactionResponse;
import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.entity.Outbox;
import com.MyProject.comment_service.enums.CommentReactionType;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Reactions (like/love) live in Redis as the fast, authoritative path - every click only
 * touches Redis, never Mongo directly, so a burst of clicks never hammers the database.
 * A separate scheduled job ({@link CommentReactionFlushJob}) periodically folds the Redis
 * counters back into the Comment document as a durable fallback. Reads (getComments/getReplies)
 * prefer the live Redis counters and only fall back to the persisted Mongo counts on a cache miss.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentReactionService {
    static final String DIRTY_SET_KEY = "comment:reaction:dirty";

    CommentRepository commentRepository;
    RedisService redisService;
    RedisTemplate<String, String> redisTemplate;
    CommentReactionNotificationService commentReactionNotificationService;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    private String userReactionKey(String commentId) {
        return "comment:reaction:user:" + commentId;
    }

    static String countKey(String commentId) {
        return "comment:reaction:count:" + commentId;
    }

    public CommentReactionResponse react(String commentId, String userId, CommentReactionType type) {
        if (type == null) {
            throw new AppException(ErrorCode.INVALID_REACTION_TYPE);
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

        String userKey = userReactionKey(commentId);
        String countKey = countKey(commentId);

        Object currentRaw = redisTemplate.opsForHash().get(userKey, userId);
        String current = currentRaw != null ? currentRaw.toString() : null;

        String myReaction;
        if (type.name().equals(current)) {
            // Same reaction clicked again -> toggle off
            redisTemplate.opsForHash().delete(userKey, userId);
            redisService.hashIncrement(countKey, type.name(), -1);
            myReaction = null;
        } else {
            if (current != null) {
                redisService.hashIncrement(countKey, current, -1);
            }
            redisTemplate.opsForHash().put(userKey, userId, type.name());
            redisService.hashIncrement(countKey, type.name(), 1);
            myReaction = type.name();
        }

        try {
            redisService.addSet(DIRTY_SET_KEY, commentId);
        } catch (Exception e) {
            log.warn("Failed to mark comment {} dirty for reaction flush", commentId, e);
        }

        if (!userId.equals(comment.getUserId())) {
            commentReactionNotificationService.scheduleNotification(commentId, userId);
        }

        Map<Object, Object> counts = redisService.hashGetAll(countKey);
        CommentReactionResponse response = CommentReactionResponse.builder()
                .commentId(commentId)
                .likeCount((int) nonNegative(parseCount(counts.get(CommentReactionType.LIKE.name()))))
                .loveCount((int) nonNegative(parseCount(counts.get(CommentReactionType.LOVE.name()))))
                .myReaction(myReaction)
                .build();

        publishRealtimeEvent(comment, userId, response);
        return response;
    }

    private void publishRealtimeEvent(Comment comment, String actorUserId, CommentReactionResponse response) {
        try {
            CommentReactionChangedEvent event = CommentReactionChangedEvent.builder()
                    .commentId(comment.getId())
                    .sourceId(comment.getSourceId())
                    .parentId(comment.getParentId())
                    .actorUserId(actorUserId)
                    .likeCount(response.getLikeCount())
                    .loveCount(response.getLoveCount())
                    .myReaction(response.getMyReaction())
                    .build();
            outboxRepository.save(Outbox.builder()
                    .aggregateId(comment.getId())
                    .topic("comment.reactions")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            // The reaction already succeeded in Redis; do not roll it back merely because the
            // realtime fan-out is temporarily unavailable. Other clients will reconcile on GET.
            log.error("Failed to publish realtime reaction event for comment {}", comment.getId(), e);
        }
    }

    /** Reads current live counts from Redis; falls back to the persisted Mongo values on a cache miss. */
    public int[] getCounts(String commentId, int fallbackLikeCount, int fallbackLoveCount) {
        try {
            Map<Object, Object> counts = redisService.hashGetAll(countKey(commentId));
            if (counts.isEmpty()) {
                return new int[]{fallbackLikeCount, fallbackLoveCount};
            }
            return new int[]{
                    (int) nonNegative(parseCount(counts.get(CommentReactionType.LIKE.name()))),
                    (int) nonNegative(parseCount(counts.get(CommentReactionType.LOVE.name())))
            };
        } catch (Exception e) {
            log.error("Failed to read reaction counts for comment {}", commentId, e);
            return new int[]{fallbackLikeCount, fallbackLoveCount};
        }
    }

    /** Returns "LIKE"/"LOVE"/null for the given viewer, or null when viewer is anonymous/unknown. */
    public String getUserReaction(String commentId, String userId) {
        if (userId == null) {
            return null;
        }
        try {
            Object raw = redisTemplate.opsForHash().get(userReactionKey(commentId), userId);
            return raw != null ? raw.toString() : null;
        } catch (Exception e) {
            log.error("Failed to read reaction for user {} on comment {}", userId, commentId, e);
            return null;
        }
    }

    private static long parseCount(Object raw) {
        if (raw == null) return 0;
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }
}
