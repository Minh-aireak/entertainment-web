package com.MyProject.post.post_service.service;

import com.MyProject.common_dto.event.dto.ProfileUpdatedEvent;
import com.MyProject.common_dto.event.dto.UserProfileResponse;
import com.MyProject.post.post_service.configuration.DateTimeFormatter;
import com.MyProject.post.post_service.dto.response.StatusResponse;
import com.MyProject.post.post_service.job.UpdatePostStatusJob;
import com.MyProject.post.post_service.dto.request.DataWeatherRequest;
import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.request.ScheduleUpdateRequest;
import com.MyProject.post.post_service.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.entity.PostType;
import com.MyProject.post.post_service.entity.TravelItinerary;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostRepository;
import com.MyProject.post.post_service.repository.TravelItineraryRepository;
import com.MyProject.post.post_service.repository.httpclient.ProfileClient;
import com.MyProject.post.post_service.repository.httpclient.WeatherClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    TravelItineraryRepository travelItineraryRepository;
    PostMapper postMapper;
    DateTimeFormatter dateTimeFormatter;
    ProfileClient client;
    WeatherClient weatherClient;
    Scheduler scheduler;

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private String calculateStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now){
        String status = "On going";
        if (start.isAfter(now)){
            status = "Up coming";
        } else if (end.isBefore(now)) {
            status = "Completed";
        }
        return status;
    }

    private Post buildBasePost(ScheduleRequest request) {
        String userId = getUserId();
        UserProfileResponse info = client.getProfile(userId).getResult();

        return Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .displayName(info.getDisplayName())
                .avatar(info.getAvatar())
                .title(request.getTitle())
                .content(request.getContent())
                .createdDate(LocalDateTime.now())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()))
                .startJobKey(null)
                .endJobKey(null)
                .listUserJoin(List.of(userId))
                .build();
    }

    private void scheduleStatusJobs(Post post) {
        String postId = post.getId();

        // ========== JOB 1: START (chuyển sang ONGOING) ==========
        if (post.getStatus().equals("Up coming")) {
            String startJobKey = "start-post-" + postId;

            JobDetail startJob = JobBuilder.newJob(UpdatePostStatusJob.class)
                    .withIdentity(startJobKey, "post-status-jobs")
                    .withDescription("Start post: " + post.getTitle())
                    .usingJobData("postId", postId)
                    .usingJobData("action", "START")
                    .storeDurably(false)
                    .build();

            Trigger startTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + startJobKey, "post-triggers")
                    .startAt(Date.from(post.getStartTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()))
                    .forJob(startJob)
                    .build();

            try {
                scheduler.scheduleJob(startJob, startTrigger);
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.SCHEDULER_EXCEPTION);
            }

            post.setStartJobKey(startJobKey);
        }

        // ========== JOB 2: END (chuyển sang COMPLETED) ==========
        if (post.getStatus().equals("On going")) {
            String endJobKey = "end-post-" + postId;

            JobDetail endJob = JobBuilder.newJob(UpdatePostStatusJob.class)
                    .withIdentity(endJobKey, "post-status-jobs")
                    .withDescription("End post: " + post.getTitle())
                    .usingJobData("postId", postId)
                    .usingJobData("action", "END")
                    .storeDurably(false)
                    .build();

            Trigger endTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + endJobKey, "post-triggers")
                    .startAt(Date.from(post.getEndTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()))
                    .forJob(endJob)
                    .build();

            try {
                scheduler.scheduleJob(endJob, endTrigger);
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.SCHEDULER_EXCEPTION);
            }

            post.setEndJobKey(endJobKey);
        }

        postRepository.save(post);
    }

    public void cancelPost(String postId) {
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        // Xóa START job
        if (post.getStartJobKey() != null) {
            try {
                scheduler.deleteJob(new JobKey(post.getStartJobKey(), "post-status-jobs")
                );
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.DELETE_JOB);
            }
        }

        // Xóa END job
        if (post.getEndJobKey() != null) {
            try {
                scheduler.deleteJob(new JobKey(post.getEndJobKey(), "post-status-jobs")
                );
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.DELETE_JOB);
            }
        }

        post.setStartJobKey(null);
        post.setEndJobKey(null);
        postRepository.save(post);
    }

    @Transactional
    public ScheduleResponse createPost(ScheduleRequest request){
        var basePost = buildBasePost(request);
        if (request.getPostType().equals("BUSINESS_SCHEDULE")) {
            basePost.setPostType(PostType.BUSINESS_SCHEDULE);
            var savedPost = postRepository.save(basePost);
            scheduleStatusJobs(savedPost);

            return postMapper.toScheduleResponse(savedPost);
        } else {
            var weatherStartResponse = weatherClient.getDataWeather(DataWeatherRequest.builder()
                            .lat(request.getLatStart())
                            .lon(request.getLonStart())
                            .build());
            var weatherEndResponse = weatherClient.getDataWeather(DataWeatherRequest.builder()
                    .lat(request.getLatStart())
                    .lon(request.getLonEnd())
                    .build());
            TravelItinerary travelItinerary = TravelItinerary.fromPost(basePost)
                    .postType(PostType.TRAVEL_ITINERARY)
                    .startPosition(weatherStartResponse.getResult())
                    .endPosition(weatherEndResponse.getResult())
                    .build();
            var savedItinerary = travelItineraryRepository.save(travelItinerary);
            scheduleStatusJobs(savedItinerary);

            return postMapper.toTravelItineraryResponse(savedItinerary);
        }
    }

    @Transactional
    public PageResponse<ScheduleResponse> getMyPosts(int page, int size, String type){
        String userId = getUserId();

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
            if (post instanceof TravelItinerary travelItinerary){
                response = postMapper.toTravelItineraryResponse(travelItinerary);
            } else {
                response = postMapper.toScheduleResponse(post);
            }
            response.setCreatedDate(dateTimeFormatter.format(post.getCreatedDate()));
            response.setPostType(post.getPostType().toString());
            return response;
        }).toList();

            return PageResponse.<ScheduleResponse>builder()
                    .currentPage(page)
                    .pageSize(size)
                    .totalPages(pageData.getTotalPages())
                    .totalElement(pageData.getTotalElements())
                    .data(postList)
                    .build();
    }

    @Transactional
    public ScheduleResponse getMyPost(String id, String type){
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            var schedule = postRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));

            return postMapper.toScheduleResponse(schedule);
        } else {
            var schedule = travelItineraryRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));

            return postMapper.toTravelItineraryResponse(schedule);
        }
    }

    @Transactional
    public ScheduleResponse updatePost(String id, String type, ScheduleUpdateRequest request){

        cancelPost(id);

        if (type.equals(PostType.BUSINESS_SCHEDULE.name())){
            var schedule = postRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));

            postMapper.updateBusinessSchedule(schedule, request);
            schedule.setStatus(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()));
            scheduleStatusJobs(schedule);

            return postMapper.toScheduleResponse(postRepository.save(schedule));
        } else {
            var schedule = travelItineraryRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));
            if (!request.getLatStart().isEmpty() && !request.getLonStart().isEmpty()) {
                var dataWeatherChange = weatherClient.getDataWeather(DataWeatherRequest.builder()
                        .lat(request.getLatStart())
                        .lon(request.getLonStart())
                        .build());
                schedule.setStartPosition(dataWeatherChange.getResult());
            }

            if (!request.getLatEnd().isEmpty() && !request.getLonEnd().isEmpty()) {
                var dataWeatherChange = weatherClient.getDataWeather(DataWeatherRequest.builder()
                        .lat(request.getLatEnd())
                        .lon(request.getLonEnd())
                        .build());
                schedule.setEndPosition(dataWeatherChange.getResult());
            }
            postMapper.updateTravelItinerary(schedule, request);
            schedule.setStatus(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()));
            scheduleStatusJobs(schedule);

            return postMapper.toTravelItineraryResponse(travelItineraryRepository.save(schedule));
        }
    }

    @Transactional
    public void deletePost(String id, String type){
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            var businessSchedule = postRepository.findByIdType(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));
            postRepository.delete(businessSchedule);
        } else {
            var travelItinerary = travelItineraryRepository.findByIdType(id).orElseThrow(() ->
                    new AppException(ErrorCode.TRAVEL_ITINERARY_NOT_EXISTED));
            travelItineraryRepository.delete(travelItinerary);
        }
    }

    public void updateUserProfile(ProfileUpdatedEvent profileUpdatedEvent) {
        var businessPosts = postRepository.findAllByUserIdForUpdate(profileUpdatedEvent.getUserId());
        businessPosts.forEach(post -> {
            post.setDisplayName(profileUpdatedEvent.getDisplayName());
            post.setAvatar(profileUpdatedEvent.getAvatar());
            postRepository.save(post);
        });

        var travelPosts = travelItineraryRepository.findAllByUserIdForUpdate(profileUpdatedEvent.getUserId());
        travelPosts.forEach(post -> {
            post.setDisplayName(profileUpdatedEvent.getDisplayName());
            post.setAvatar(profileUpdatedEvent.getAvatar());
            travelItineraryRepository.save(post);
        });
    }

    public StatusResponse getStatus() {
        return StatusResponse.builder()
                .quantityOnGoing(postRepository.countByStatus("On going"))
                .quantityUpComing(postRepository.countByStatus("Up coming"))
                .build();
    }
}
