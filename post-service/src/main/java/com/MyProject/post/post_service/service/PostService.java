package com.MyProject.post.post_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.post.post_service.dto.request.PostRequest;
import com.MyProject.post.post_service.dto.request.PostUpdateRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.dto.response.StatusResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.entity.PostLike;
import com.MyProject.post.post_service.entity.PostType;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostElasticRepository;
import com.MyProject.post.post_service.repository.PostLikeRepository;
import com.MyProject.post.post_service.repository.PostRepository;
import com.MyProject.common.security.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    PostElasticRepository postElasticRepository;
    PostLikeRepository postLikeRepository;
    PostMapper postMapper;
    PostCacheService postCacheService;
    OutboxEventPublisher outboxEventPublisher;
    MongoTemplate mongoTemplate;
    PostProfileExternalService postProfileExternalService;
    PostFileExternalService postFileExternalService;
    PostLikeNotificationService postLikeNotificationService;

    private static final String STATUS_ONGOING = "On going";
    private static final String STATUS_UPCOMING = "Up coming";
    private static final String POST_COLLECTION = "post";
    private static final int MAX_RANDOM_LIMIT = 20;

    private PostType parsePostType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new AppException(ErrorCode.INVALID_POST_TYPE);
        }
        try {
            return PostType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_POST_TYPE);
        }
    }

    private void validateTypedData(PostType postType, PostRequest request) {
        switch (postType) {
            case IMAGE:
                if (request.getImageFileIds() == null || request.getImageFileIds().isEmpty()) {
                    throw new AppException(ErrorCode.IMAGE_POST_EMPTY_IDS);
                }
                break;
            case WATCH_TOGETHER:
                if (request.getWatchRoomId() == null || request.getWatchInviteCode() == null
                        || request.getWatchRoomId().isBlank() || request.getWatchInviteCode().isBlank()) {
                    throw new AppException(ErrorCode.WATCH_POST_MISSING_ROOM);
                }
                break;
            case TEXT:
            default:
                break;
        }
    }

    private Post buildBasePost(PostRequest request, PostType postType) {
        String userId = SecurityUtils.getCurrentUserId();
        List<String> listJoins = request.getListUsersJoin();
        if (listJoins == null) {
            listJoins = new ArrayList<>();
        }
        if (!listJoins.contains(userId)) {
            listJoins.add(userId);
        }
        return Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .postType(postType)
                .title(request.getTitle())
                .content(request.getContent())
                .backgroundColor(postType == PostType.TEXT ? request.getBackgroundColor() : null)
                .feeling(request.getFeeling())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdDate(LocalDateTime.now())
                .modifiedDate(null)
                .status(null)
                .listUsersJoin(listJoins)
                .imageFileIds(request.getImageFileIds())
                .watchRoomId(request.getWatchRoomId())
                .watchFilmId(request.getWatchFilmId())
                .watchEpisodeId(request.getWatchEpisodeId())
                .watchInviteCode(request.getWatchInviteCode())
                .watchFilmTitle(request.getWatchFilmTitle())
                .watchFilmThumbnailFileId(request.getWatchFilmThumbnailFileId())
                .watchParticipantCount(1)
                .likeCount(0)
                .build();
    }

    @Transactional
    public PostResponse createPost(PostRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        PostType postType = parsePostType(request.getPostType());
        validateTypedData(postType, request);

        Post post = buildBasePost(request, postType);
        Post saved = postRepository.save(post);
        outboxEventPublisher.publish("post.sync", saved.getId(), postMapper.toPostDoc(saved));
        postCacheService.invalidateAllUserPosts(userId);
        return enrichSingle(postMapper.toPostResponse(saved));
    }

    @Transactional
    public PageResponse<PostResponse> getMyPosts(int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();

        PageResponse<PostResponse> cached = postCacheService.getCachedPosts(userId, page, size);
        if (cached != null) {
            // Profile data is intentionally refreshed even on a cache hit so an older cached page
            // cannot keep returning a missing/stale display name or presigned avatar URL.
            enrichAuthorProfiles(cached.getData());
            return cached;
        }

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, sort);

        Page<Post> pageData = postRepository.findAllByUserId(userId, pageable);

        List<PostResponse> postList = pageData.getContent().stream().map(post -> {
            PostResponse response = postMapper.toPostResponse(post);
            response.setPostType(post.getPostType().name());
            return response;
        }).toList();

        List<String> postIds = postList.stream().map(PostResponse::getId).toList();
        if (!postIds.isEmpty()) {
            Set<String> likedPostIds = postLikeRepository.findByPostIdInAndUserId(postIds, userId).stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());
            postList.forEach(response -> response.setLiked(likedPostIds.contains(response.getId())));
        }

        List<PostResponse> enriched = enrichAuthorProfiles(postList);
        enriched = enrichFileUrls(enriched);

        PageResponse<PostResponse> result = PageResponse.<PostResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(enriched)
                .build();

        postCacheService.cachePosts(userId, page, size, result);
        return result;
    }

    @Transactional
    public PostResponse getMyPost(String id) {
        String userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        PostResponse response = postMapper.toPostResponse(post);
        response.setPostType(post.getPostType().name());
        response.setLiked(postLikeRepository.existsByPostIdAndUserId(id, userId));
        return enrichSingle(response);
    }

    @Transactional
    public LikeResponse toggleLike(String id) {
        String userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(id, userId);
        if (alreadyLiked) {
            postLikeRepository.deleteByPostIdAndUserId(id, userId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            postLikeRepository.save(PostLike.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(id)
                    .userId(userId)
                    .createdDate(LocalDateTime.now())
                    .build());
            post.setLikeCount(post.getLikeCount() + 1);
        }
        postRepository.save(post);
        postCacheService.invalidateAllUserPosts(userId);

        boolean isNowLiked = !alreadyLiked;
        if (!userId.equals(post.getUserId())) {
            postLikeNotificationService.scheduleNotification(id, userId);
        }

        return LikeResponse.builder()
                .liked(isNowLiked)
                .likeCount(post.getLikeCount())
                .build();
    }

    @Transactional
    public PostResponse updatePost(String id, PostUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        postMapper.updatePost(post, request);
        post.setModifiedDate(LocalDateTime.now());
        Post saved = postRepository.save(post);
        outboxEventPublisher.publish("post.sync", saved.getId(), postMapper.toPostDoc(saved));
        postCacheService.invalidateAllUserPosts(userId);
        return enrichSingle(postMapper.toPostResponse(saved));
    }

    @Transactional
    public void deletePost(String id) {
        String userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        postRepository.delete(post);
        postCacheService.invalidateAllUserPosts(userId);
    }

    public StatusResponse getStatus() {
        return StatusResponse.builder()
                .quantityOnGoing(postRepository.countByStatus(STATUS_ONGOING))
                .quantityUpComing(postRepository.countByStatus(STATUS_UPCOMING))
                .build();
    }

    public PageResponse<PostResponse> searchPosts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        var searchResult = postElasticRepository.searchByTitleOrContent(query, pageable);

        List<PostResponse> data = searchResult.getContent().stream()
                .map(doc -> PostResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .content(doc.getContent())
                        .postType(doc.getPostType())
                        .build())
                .toList();

        return PageResponse.<PostResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(data)
                .build();
    }

    public Integer countByUserId() {
        return postRepository.countByUserId(SecurityUtils.getCurrentUserId());
    }

    public String getPostOwner(String postId) {
        return postRepository.findById(postId)
                .map(Post::getUserId)
                .orElse(null);
    }

    public List<PostResponse> getRandomPosts(int limit, List<String> excludeIds) {
        String userId = SecurityUtils.getCurrentUserId();
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RANDOM_LIMIT);

        List<Post> posts = sampleRandomPosts(safeLimit, excludeIds);
        if (posts.isEmpty()) {
            return List.of();
        }

        List<String> postIds = posts.stream().map(Post::getId).toList();
        Set<String> likedPostIds = postLikeRepository.findByPostIdInAndUserId(postIds, userId).stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());

        Set<String> authorIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<String, UserProfileResponse> profiles = postProfileExternalService.getBulkUserProfiles(authorIds);

        List<PostResponse> responses = posts.stream().map(post -> {
            PostResponse response = postMapper.toPostResponse(post);
            response.setPostType(post.getPostType().name());
            response.setLiked(likedPostIds.contains(post.getId()));
            UserProfileResponse profile = profiles.get(post.getUserId());
            if (profile != null) {
                response.setDisplayName(profile.getDisplayName());
                response.setAvatar(profile.getAvatar());
            }
            return response;
        }).toList();

        return enrichFileUrls(responses);
    }

    private List<Post> sampleRandomPosts(int limit, List<String> excludeIds) {
        Criteria criteria = Criteria.where("postType").in(Arrays.stream(PostType.values()).toList());
        if (excludeIds != null && !excludeIds.isEmpty()) {
            criteria = criteria.and("_id").nin(excludeIds);
        }
        return new ArrayList<>(mongoTemplate.aggregate(
                Aggregation.newAggregation(Aggregation.match(criteria), Aggregation.sample(limit)),
                POST_COLLECTION,
                Post.class
        ).getMappedResults());
    }

    private List<PostResponse> enrichFileUrls(List<PostResponse> posts) {
        if (posts == null || posts.isEmpty()) return posts;
        List<String> allFileIds = new ArrayList<>();
        for (PostResponse p : posts) {
            if (p.getImageFileIds() != null) allFileIds.addAll(p.getImageFileIds());
            if (p.getWatchFilmThumbnailFileId() != null) allFileIds.add(p.getWatchFilmThumbnailFileId());
        }
        if (allFileIds.isEmpty()) return posts;
        Map<String, String> resolved;
        try {
            resolved = postFileExternalService.resolvePresignedUrls(allFileIds);
        } catch (Exception e) {
            log.warn("Resolve file URLs failed for {} files", allFileIds.size(), e);
            return posts;
        }
        for (PostResponse p : posts) {
            if (p.getImageFileIds() != null) {
                List<String> urls = p.getImageFileIds().stream()
                        .map(resolved::get)
                        .filter(Objects::nonNull)
                        .toList();
                p.setImageUrls(urls);
            }
            if (p.getWatchFilmThumbnailFileId() != null) {
                p.setWatchFilmThumbnailUrl(resolved.get(p.getWatchFilmThumbnailFileId()));
            }
        }
        return posts;
    }

    private List<PostResponse> enrichAuthorProfiles(List<PostResponse> posts) {
        if (posts == null || posts.isEmpty()) return posts;

        Set<String> authorIds = posts.stream()
                .map(PostResponse::getUserId)
                .filter(Objects::nonNull)
                .filter(userId -> !userId.isBlank())
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) return posts;

        Map<String, UserProfileResponse> profiles = postProfileExternalService.getBulkUserProfiles(authorIds);
        if (profiles == null || profiles.isEmpty()) return posts;

        posts.forEach(post -> {
            UserProfileResponse profile = profiles.get(post.getUserId());
            if (profile != null) {
                post.setDisplayName(profile.getDisplayName());
                post.setAvatar(profile.getAvatar());
            }
        });
        return posts;
    }

    private PostResponse enrichSingle(PostResponse post) {
        Map<String, UserProfileResponse> profiles = postProfileExternalService.getBulkUserProfiles(Set.of(post.getUserId()));
        UserProfileResponse profile = profiles.get(post.getUserId());
        if (profile != null) {
            post.setDisplayName(profile.getDisplayName());
            post.setAvatar(profile.getAvatar());
        }
        List<PostResponse> enriched = enrichFileUrls(List.of(post));
        return enriched.get(0);
    }
}
