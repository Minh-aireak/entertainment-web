package com.MyProject.comment_service.service;

import com.MyProject.comment_service.configuration.DateTimeFormatter;
import com.MyProject.comment_service.dto.request.CreateCommentRequest;
import com.MyProject.comment_service.dto.request.UpdateCommentRequest;
import com.MyProject.comment_service.dto.response.CommentResponse;
import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.enums.CommentStatus;
import com.MyProject.comment_service.enums.CommentType;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.mapper.CommentMapper;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentService {
    CommentRepository commentRepository;
    CommentMapper commentMapper;
    CommentProfileExternalService commentProfileExternalService;
    RedisService redisService;
    DateTimeFormatter formatter;

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

    @Transactional(rollbackFor = Exception.class)
    public CommentResponse createComment(CreateCommentRequest request) {
        String currentId = SecurityUtils.getCurrentUserId();

        Comment comment = Comment.builder()
                .sourceId(request.getSourceId())
                .userId(currentId)
                .content(request.getContent())
                .type(request.getType())
                .parentId(request.getParentId())
                .topParentId(request.getTopParentId())
                .likeCount(0)
                .replyCount(0)
                .status(CommentStatus.SENT)
                .build();

        comment = commentRepository.save(comment);

        // Invalidate all comment caches for this source
        try {
            redisService.deletePattern("comment:" + request.getSourceId() + ":*");
        } catch (Exception e) {
            log.error("Failed to delete cache", e);
        }

        CommentResponse response = commentMapper.toCommentResponse(comment);
        enrichCommentResponse(response, comment);

        return response;
    }

    public PageResponse<CommentResponse> getComments(String sourceId, int page, int size) {
        String cacheKey = buildCacheKey(sourceId, page, size);

        // Caching for first page (TTL 60s)
        if (page == 1) {
            try {
                PageResponse<CommentResponse> cachedResponse = redisService.get(cacheKey, new TypeReference<PageResponse<CommentResponse>>() {});
                if (cachedResponse != null) {
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

        // Cache first page
        if (page == 1) {
            try {
                redisService.setWithExpiration(cacheKey, response, 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to cache comments", e);
            }
        }

        return response;
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

        comment.setContent(request.getContent());
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
        commentRepository.save(comment);

        // Invalidate all comment caches for this source
        try {
            redisService.deletePattern("comment:" + comment.getSourceId() + ":*");
        } catch (Exception e) {
            log.error("Failed to delete cache", e);
        }
    }
}
