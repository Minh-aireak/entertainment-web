package com.MyProject.comment_service.service;

import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.enums.CommentReactionType;
import com.MyProject.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Periodically folds the live Redis reaction counters back into the Comment document,
 * so Mongo stays an eventually-consistent durable fallback without taking a write on every
 * single like/love click (that hot path lives entirely in {@link CommentReactionService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentReactionFlushJob {
    private final RedisService redisService;
    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelay = 30_000)
    public void flushReactionCounts() {
        Set<String> dirtyCommentIds;
        try {
            dirtyCommentIds = redisService.getSetMembers(CommentReactionService.DIRTY_SET_KEY);
        } catch (Exception e) {
            log.error("Failed to read dirty reaction set", e);
            return;
        }
        if (dirtyCommentIds.isEmpty()) {
            return;
        }

        for (String commentId : dirtyCommentIds) {
            try {
                Map<Object, Object> counts = redisService.hashGetAll(CommentReactionService.countKey(commentId));
                long likeCount = Math.max(0, parseCount(counts.get(CommentReactionType.LIKE.name())));
                long loveCount = Math.max(0, parseCount(counts.get(CommentReactionType.LOVE.name())));

                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(commentId)),
                        new Update().set("likeCount", (int) likeCount).set("loveCount", (int) loveCount),
                        Comment.class
                );

                redisService.removeSet(CommentReactionService.DIRTY_SET_KEY, commentId);
            } catch (Exception e) {
                log.error("Failed to flush reaction counts for comment {}", commentId, e);
            }
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
}
