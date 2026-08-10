package com.MyProject.comment_service.service;

import com.MyProject.comment_service.configuration.DateTimeFormatter;
import com.MyProject.comment_service.dto.event.CommentCreatedEvent;
import com.MyProject.comment_service.dto.request.CreateCommentRequest;
import com.MyProject.comment_service.dto.request.UpdateCommentRequest;
import com.MyProject.comment_service.dto.response.CommentResponse;
import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.entity.Outbox;
import com.MyProject.comment_service.enums.CommentStatus;
import com.MyProject.comment_service.enums.CommentType;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.mapper.CommentMapper;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentService {
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final String COMMENT_COLLECTION = "comments";

    CommentRepository commentRepository;
    CommentMapper commentMapper;
    CommentProfileExternalService commentProfileExternalService;
    CommentPostExternalService commentPostExternalService;
    CommentReactionService commentReactionService;
    RedisService redisService;
    DateTimeFormatter formatter;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;
    MongoTemplate mongoTemplate;

    private String buildCacheKey(String sourceId, int page, int size) {
        return "comment:" + sourceId + ":page:" + page + ":size:" + size;
    }

    private void enrichCommentResponse(CommentResponse response, Comment comment) {
        response.setDurationCreatedDate(formatter.format(comment.getCreatedDate()));
        try {
            UserProfileResponse profile = redisService.get("profile:user:" + comment.getUserId(), new TypeReference<UserProfileResponse>() {});
            if (profile != null) {
                response.setAvatar(profile.getAvatar());
                response.setDisplayName(profile.getDisplayName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user profile from cache for user: {}", comment.getUserId(), e);
        }
    }

    /**
     * Overlays the live Redis reaction counts/viewer state onto an already-mapped response.
     * Uses the response's own likeCount/loveCount (whatever was mapped from Mongo, cached or fresh)
     * as the fallback when Redis has no entry - MUST be called AFTER any caching of the response,
     * since myReaction is viewer-specific and must never be written into the shared page-1 cache.
     */
    private void applyReactionOverlay(CommentResponse response, String viewerId) {
        int[] counts = commentReactionService.getCounts(response.getId(), response.getLikeCount(), response.getLoveCount());
        response.setLikeCount(counts[0]);
        response.setLoveCount(counts[1]);
        response.setMyReaction(commentReactionService.getUserReaction(response.getId(), viewerId));
    }

    /** Same as SecurityUtils.getCurrentUserId() but returns null for anonymous viewers instead of throwing - GET endpoints are public. */
    private String getCurrentUserIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentResponse createComment(CreateCommentRequest request) {
        String currentId = SecurityUtils.getCurrentUserId();
        String content = validateAndNormalizeContent(request.getContent());

        Comment comment = Comment.builder()
                .sourceId(request.getSourceId())
                .userId(currentId)
                .content(content)
                .type(request.getType())
                .parentId(request.getParentId())
                .topParentId(request.getTopParentId())
                .likeCount(0)
                .loveCount(0)
                .replyCount(0)
                .status(CommentStatus.SENT)
                .build();

        comment = commentRepository.save(comment);

        // Reply -> atomically bump the parent's replyCount in the same transaction (partial
        // update, does not touch the parent's modifiedDate/auditing).
        if (comment.getParentId() != null) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(comment.getParentId())),
                    new Update().inc("replyCount", 1),
                    Comment.class
            );
        }

        // Invalidate all comment caches for this source
        try {
            redisService.deletePattern("comment:" + request.getSourceId() + ":*");
        } catch (Exception e) {
            log.error("Failed to delete cache", e);
        }

        CommentResponse response = commentMapper.toCommentResponse(comment);
        enrichCommentResponse(response, comment);

        // Ghi Outbox event trong CÙNG transaction Mongo với comment vừa lưu.
        // Không emit socket trực tiếp ở đây - Debezium CDC sẽ đọc collection "outbox"
        // và relay qua Kafka topic "comment.events" -> socket-service broadcast.
        publishCommentEvent("COMMENT_CREATED", response);

        publishInteractionNotifications(comment);

        return response;
    }

    private String validateAndNormalizeContent(String content) {
        if (content == null) {
            throw new AppException(ErrorCode.COMMENT_CONTENT_EMPTY);
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new AppException(ErrorCode.COMMENT_CONTENT_EMPTY);
        }
        // codePointCount thay vì length() để đếm đúng số ký tự khi content chứa emoji (surrogate pairs)
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_CONTENT_LENGTH) {
            throw new AppException(ErrorCode.COMMENT_CONTENT_TOO_LONG);
        }
        return trimmed;
    }

    private void publishCommentEvent(String eventType, CommentResponse response) {
        saveToOutbox(response.getId(), "comment.events", CommentCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now())
                .producer("comment-service")
                .comment(response)
                .build());
    }

    private void saveToOutbox(String aggregateId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save outbox with topic: {}", topic, e);
            throw new AppException(ErrorCode.OUTBOX_SAVE_FAILED);
        }
    }

    public PageResponse<CommentResponse> getComments(String sourceId, int page, int size) {
        String cacheKey = buildCacheKey(sourceId, page, size);
        String viewerId = getCurrentUserIdOrNull();

        // Caching for first page (TTL 60s). Cached payload never carries reaction data
        // (myReaction is per-viewer) - the live reaction overlay is applied after this
        // method decides whether it served a cache hit or a fresh Mongo fetch.
        if (page == 1) {
            try {
                PageResponse<CommentResponse> cachedResponse = redisService.get(cacheKey, new TypeReference<PageResponse<CommentResponse>>() {});
                if (cachedResponse != null) {
                    cachedResponse.getData().forEach(response -> applyReactionOverlay(response, viewerId));
                    return cachedResponse;
                }
            } catch (Exception e) {
                log.error("Failed to get comments from cache", e);
            }
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdDate").descending());
        Page<Comment> commentPage = commentRepository
                .findBySourceIdAndParentIdIsNullAndStatusNot(sourceId, CommentStatus.DELETED, pageable);

        List<CommentResponse> commentResponses = commentPage.getContent().stream()
                .map(comment -> {
                    CommentResponse response = commentMapper.toCommentResponse(comment);
                    response.setDurationCreatedDate(formatter.format(comment.getCreatedDate()));
                    return response;
                }).collect(Collectors.toList());

        // Fetch User Profiles
        Set<String> userIds = commentResponses.stream()
                .map(CommentResponse::getUserId)
                .collect(Collectors.toSet());

        if (!userIds.isEmpty()) {
            Map<String, UserProfileResponse> profilesMap = new HashMap<>();
            Set<String> missingUserIds = new HashSet<>();

            // 1. Check Redis first
            List<String> keys = userIds.stream()
                    .map(id -> "profile:user:" + id)
                    .toList();

            try {
                List<UserProfileResponse> cachedProfiles = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
                // Filter out null profiles and collect to map
                Map<String, UserProfileResponse> cachedMap = cachedProfiles.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(UserProfileResponse::getUserId, p -> p, (p1, p2) -> p1));
                
                profilesMap.putAll(cachedMap);
            } catch (Exception e) {
                log.error("Failed to fetch profiles from Redis", e);
            }

            // 2. Identify missing profiles
            userIds.forEach(id -> {
                if (!profilesMap.containsKey(id)) {
                    missingUserIds.add(id);
                }
            });

            // 3. Fetch missing from ProfileClient
            if (!missingUserIds.isEmpty()) {
                try {
                    Map<String, UserProfileResponse> fetchedProfiles =
                            commentProfileExternalService.getBulkUserProfiles(missingUserIds);

                    if (fetchedProfiles != null && !fetchedProfiles.isEmpty()) {
                        profilesMap.putAll(fetchedProfiles);

                        // 4. Cache fetched profiles back to Redis
                        fetchedProfiles.forEach((id, profile) -> {
                            try {
                                redisService.setWithExpiration("profile:user:" + id, profile, 1, TimeUnit.HOURS);
                            } catch (Exception e) {
                                log.error("Failed to cache profile for user: {}", id, e);
                            }
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch user profiles from client", e);
                }
            }

            // 5. Map profiles to responses
            commentResponses.forEach(response -> {
                UserProfileResponse profile = profilesMap.get(response.getUserId());
                if (profile != null) {
                    response.setAvatar(profile.getAvatar());
                    response.setDisplayName(profile.getDisplayName());
                }
            });
        }

        PageResponse<CommentResponse> response = PageResponse.<CommentResponse>builder()
                .data(commentResponses)
                .currentPage(page)
                .pageSize(size)
                .totalElement(commentPage.getTotalElements())
                .totalPages(commentPage.getTotalPages())
                .build();

        // Cache first page (base/shared data only)
        if (page == 1) {
            try {
                redisService.setWithExpiration(cacheKey, response, 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to cache comments", e);
            }
        }

        // Overlay live reaction counts + this viewer's own reaction state (never cached above)
        commentResponses.forEach(r -> applyReactionOverlay(r, viewerId));

        return response;
    }

    /**
     * Total comment count for a post INCLUDING replies - unlike getComments' totalElement
     * (top-level only, used to paginate the root-comment list), this is the number meant for
     * display next to a "comment" icon/badge. replyCount on each top-level comment is kept in
     * sync on every reply create/delete (see createComment/deleteComment), so summing it avoids
     * a second query against the replies themselves.
     */
    public long countAllComments(String sourceId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("sourceId").is(sourceId)
                        .and("parentId").isNull()
                        .and("status").ne(CommentStatus.DELETED)),
                Aggregation.group()
                        .count().as("topLevelCount")
                        .sum("replyCount").as("totalReplies")
        );
        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, COMMENT_COLLECTION, Document.class);
        Document doc = results.getUniqueMappedResult();
        if (doc == null) return 0;
        long topLevelCount = ((Number) doc.get("topLevelCount")).longValue();
        Object totalRepliesObj = doc.get("totalReplies");
        long totalReplies = totalRepliesObj != null ? ((Number) totalRepliesObj).longValue() : 0;
        return topLevelCount + totalReplies;
    }

    public PageResponse<CommentResponse> getReplies(String parentId, int page, int size) {
        String viewerId = getCurrentUserIdOrNull();

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdDate").ascending());
        Page<Comment> replyPage = commentRepository.findByParentIdAndStatusNot(parentId, CommentStatus.DELETED, pageable);

        List<CommentResponse> replyResponses = replyPage.getContent().stream()
                .map(comment -> {
                    CommentResponse response = commentMapper.toCommentResponse(comment);
                    enrichCommentResponse(response, comment);
                    applyReactionOverlay(response, viewerId);
                    return response;
                }).collect(Collectors.toList());

        return PageResponse.<CommentResponse>builder()
                .data(replyResponses)
                .currentPage(page)
                .pageSize(size)
                .totalElement(replyPage.getTotalElements())
                .totalPages(replyPage.getTotalPages())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public CommentResponse updateComment(String commentId, UpdateCommentRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (comment.getType() != CommentType.TEXT) {
            throw new AppException(ErrorCode.INVALID_COMMENT_TYPE);
        }

        comment.setContent(validateAndNormalizeContent(request.getContent()));
        comment.setStatus(CommentStatus.EDITED);
        comment = commentRepository.save(comment);

        // Invalidate all comment caches for this source
        try {
            redisService.deletePattern("comment:" + comment.getSourceId() + ":*");
        } catch (Exception e) {
            log.error("Failed to delete cache", e);
        }

        CommentResponse response = commentMapper.toCommentResponse(comment);
        enrichCommentResponse(response, comment);
        applyReactionOverlay(response, currentUserId);

        // Same outbox mechanism as create - relay picks eventType up from the payload and
        // broadcasts "comment:updated" to the sourceId room so every viewer (including replies
        // rendered under it) stays in sync without a manual refresh.
        publishCommentEvent("COMMENT_UPDATED", response);

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(String commentId) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        comment.setStatus(CommentStatus.DELETED);
        comment = commentRepository.save(comment);

        if (comment.getParentId() != null) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(comment.getParentId())),
                    new Update().inc("replyCount", -1),
                    Comment.class
            );
        }

        CommentResponse response = commentMapper.toCommentResponse(comment);
        enrichCommentResponse(response, comment);
        publishCommentEvent("COMMENT_DELETED", response);

        // Invalidate all comment caches for this source
        try {
            redisService.deletePattern("comment:" + comment.getSourceId() + ":*");
        } catch (Exception e) {
            log.error("Failed to delete cache", e);
        }
    }

    private void publishInteractionNotifications(Comment comment) {
        String actorId = comment.getUserId();
        String postId = comment.getSourceId();

        String postOwnerId = commentPostExternalService.getPostOwner(postId);
        if (postOwnerId == null) {
            return;
        }

        String parentOwnerId = null;
        if (comment.getParentId() != null) {
            parentOwnerId = commentRepository.findById(comment.getParentId())
                    .map(Comment::getUserId)
                    .orElse(null);
        }

        Set<String> postCommentRecipients = new LinkedHashSet<>();
        if (!actorId.equals(postOwnerId)) {
            postCommentRecipients.add(postOwnerId);
        }

        Set<String> replyRecipients = new LinkedHashSet<>();
        if (parentOwnerId != null
                && !actorId.equals(parentOwnerId)
                && !parentOwnerId.equals(postOwnerId)) {
            replyRecipients.add(parentOwnerId);
        }

        if (!postCommentRecipients.isEmpty()) {
            saveNotificationEventOutbox(
                    UUID.randomUUID().toString(),
                    "SOCIAL_COMMENT",
                    actorId,
                    new ArrayList<>(postCommentRecipients)
            );
        }
        if (!replyRecipients.isEmpty()) {
            saveNotificationEventOutbox(
                    UUID.randomUUID().toString(),
                    "COMMENT_REPLY",
                    actorId,
                    new ArrayList<>(replyRecipients)
            );
        }
    }

    private void saveNotificationEventOutbox(String aggregateId, String type, String sender, List<String> toUserIds) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("typeNotification", type);
            event.put("userIdSender", sender);
            event.put("toUserIds", toUserIds);

            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregateId)
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save notification outbox type={}", type, e);
        }
    }
}
