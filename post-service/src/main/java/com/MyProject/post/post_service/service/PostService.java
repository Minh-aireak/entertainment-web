package com.MyProject.post.post_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.request.ScheduleUpdateRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.dto.response.StatusResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.entity.PostLike;
import com.MyProject.post.post_service.entity.PostType;
import com.MyProject.post.post_service.entity.TravelItinerary;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostElasticRepository;
import com.MyProject.post.post_service.repository.PostLikeRepository;
import com.MyProject.post.post_service.repository.PostRepository;
import com.MyProject.post.post_service.repository.TravelItineraryRepository;
import com.MyProject.post.post_service.service.DateTimeFormatter;
import com.MyProject.common.security.SecurityUtils;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    TravelItineraryRepository travelItineraryRepository;
    PostElasticRepository postElasticRepository;
    PostLikeRepository postLikeRepository;
    PostMapper postMapper;
    DateTimeFormatter dateTimeFormatter;
    PostJobManagementService postJobManagementService;
    PostCacheService postCacheService;
    OutboxEventPublisher outboxEventPublisher;

    private static final String STATUS_ONGOING = "On going";
    private static final String STATUS_UPCOMING = "Up coming";
    private static final String STATUS_COMPLETED = "Completed";

    private String calculateStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        String status = STATUS_ONGOING;
        if (start.isAfter(now)) {
            status = STATUS_UPCOMING;
        } else if (end.isBefore(now)) {
            status = STATUS_COMPLETED;
        }
        return status;
    }

    private Post buildBasePost(ScheduleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        List<String> listJoins = request.getListUsersJoin();
        if (listJoins == null) {
            listJoins = new java.util.ArrayList<>();
        }
        listJoins.add(userId);

        return Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdDate(LocalDateTime.now())
                .modifiedDate(null)
                .status(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()))
                .startJobKey(null)
                .endJobKey(null)
                .listUsersJoin(listJoins)
                .build();
    }

    @Transactional
    public ScheduleResponse createPost(ScheduleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        var basePost = buildBasePost(request);
        if (request.getPostType().equals(PostType.BUSINESS_SCHEDULE.name())) {
            basePost.setPostType(PostType.BUSINESS_SCHEDULE);
            var savedPost = postRepository.save(basePost);
            postJobManagementService.scheduleStatusJobs(savedPost);
            outboxEventPublisher.publish("post.sync", savedPost.getId(), postMapper.toPostDoc(savedPost));
            postCacheService.invalidateAllUserPosts(userId);
            return postMapper.toScheduleResponse(savedPost);
        } else {
            var travelItinerary = TravelItinerary.fromPost(basePost).build();
            var savedTravelItinerary = travelItineraryRepository.save(travelItinerary);
            postJobManagementService.scheduleStatusJobs(savedTravelItinerary);
            outboxEventPublisher.publish("post.sync", savedTravelItinerary.getId(), postMapper.toPostDoc(savedTravelItinerary));
            postCacheService.invalidateAllUserPosts(userId);
            return postMapper.toTravelItineraryResponse(savedTravelItinerary);
        }
    }

    @Transactional
    public PageResponse<ScheduleResponse> getMyPosts(int page, int size, String type) {
        String userId = SecurityUtils.getCurrentUserId();

        PageResponse<ScheduleResponse> cached = postCacheService.getCachedPosts(userId, type, page, size);
        if (cached != null) {
            return cached;
        }

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<? extends Post> pageData;
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            pageData = postRepository.findAllByUserId(userId, pageable);
        } else {
            pageData = travelItineraryRepository.findAllByUserId(userId, pageable);
        }

        List<ScheduleResponse> postList = pageData.getContent().stream().map(post -> {
            ScheduleResponse response;
            if (post instanceof TravelItinerary travelItinerary) {
                response = postMapper.toTravelItineraryResponse(travelItinerary);
            } else {
                response = postMapper.toScheduleResponse(post);
            }
            response.setCreatedDate(dateTimeFormatter.format(post.getCreatedDate()));
            response.setPostType(post.getPostType().toString());
            return response;
        }).toList();

        List<String> postIds = postList.stream().map(ScheduleResponse::getId).toList();
        if (!postIds.isEmpty()) {
            Set<String> likedPostIds = postLikeRepository.findByPostIdInAndUserId(postIds, userId).stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());
            postList.forEach(response -> response.setLiked(likedPostIds.contains(response.getId())));
        }

        PageResponse<ScheduleResponse> result = PageResponse.<ScheduleResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(postList)
                .build();

        postCacheService.cachePosts(userId, type, page, size, result);

        return result;
    }

    @Transactional
    public ScheduleResponse getMyPost(String id, String type) {
        String userId = SecurityUtils.getCurrentUserId();
        ScheduleResponse response;
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            var schedule = postRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));
            response = postMapper.toScheduleResponse(schedule);
        } else {
            var schedule = travelItineraryRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));
            response = postMapper.toTravelItineraryResponse(schedule);
        }
        response.setLiked(postLikeRepository.existsByPostIdAndUserId(id, userId));
        return response;
    }

    @Transactional
    public LikeResponse toggleLike(String id, String type) {
        String userId = SecurityUtils.getCurrentUserId();
        Post post = postRepository.findById(id).orElseThrow(() ->
                new AppException(type.equals(PostType.BUSINESS_SCHEDULE.name())
                        ? ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED
                        : ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));

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

        return LikeResponse.builder()
                .liked(!alreadyLiked)
                .likeCount(post.getLikeCount())
                .build();
    }

    @Transactional
    public ScheduleResponse updatePost(String id, String type, ScheduleUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        postJobManagementService.cancelPost(id);

        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            var schedule = postRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));
            postMapper.updateBusinessSchedule(schedule, request);
            schedule.setStatus(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()));
            schedule.setModifiedDate(LocalDateTime.now());
            postJobManagementService.scheduleStatusJobs(schedule);
            var saved = postRepository.save(schedule);
            outboxEventPublisher.publish("post.sync", saved.getId(), postMapper.toPostDoc(saved));
            postCacheService.invalidateAllUserPosts(userId);
            return postMapper.toScheduleResponse(saved);
        } else {
            var schedule = travelItineraryRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));
            postMapper.updateBusinessSchedule(schedule, request);
            schedule.setStatus(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()));
            schedule.setModifiedDate(LocalDateTime.now());
            postJobManagementService.scheduleStatusJobs(schedule);
            var saved = travelItineraryRepository.save(schedule);
            outboxEventPublisher.publish("post.sync", saved.getId(), postMapper.toPostDoc(saved));
            postCacheService.invalidateAllUserPosts(userId);
            return postMapper.toTravelItineraryResponse(saved);
        }
    }

    @Transactional
    public void deletePost(String id, String type) {
        String userId = SecurityUtils.getCurrentUserId();
        postJobManagementService.cancelPost(id);

        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            var businessSchedule = postRepository.findByIdType(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));
            postRepository.delete(businessSchedule);
        } else {
            var travelItinerary = travelItineraryRepository.findByIdType(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));
            travelItineraryRepository.delete(travelItinerary);
        }

        postCacheService.invalidateAllUserPosts(userId);
    }

    public StatusResponse getStatus() {
        return StatusResponse.builder()
                .quantityOnGoing(postRepository.countByStatus(STATUS_ONGOING))
                .quantityUpComing(postRepository.countByStatus(STATUS_UPCOMING))
                .build();
    }

    public PageResponse<ScheduleResponse> searchPosts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        var searchResult = postElasticRepository.searchByTitleOrContent(query, pageable);

        return PageResponse.<ScheduleResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(searchResult.getContent().stream()
                        .map(doc -> ScheduleResponse.builder()
                                .id(doc.getId())
                                .title(doc.getTitle())
                                .content(doc.getContent())
                                .postType(doc.getPostType())
                                .build())
                        .toList())
                .build();
    }

    public Integer countByUserId() {
        return postRepository.countByUserId(SecurityUtils.getCurrentUserId());
    }
}
