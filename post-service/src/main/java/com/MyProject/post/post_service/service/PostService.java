package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.configuration.DateTimeFormatter;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    TravelItineraryRepository travelItineraryRepository;
    PostMapper postMapper;
    DateTimeFormatter dateTimeFormatter;
    ProfileClient client;
    WeatherClient weatherClient;
//    SimpMessagingTemplate messagingTemplate;

    private Post buildBasePost(ScheduleRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        var info = client.getProfile(jwt.getClaim("userId")).getResult();

        return Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(jwt.getClaim("userId"))
                .displayName(info.getDisplayName())
                .title(request.getTitle())
                .content(request.getContent())
                .createdDate(LocalDateTime.now())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()))
                .build();
    }

    @Transactional
    public ScheduleResponse createPost(ScheduleRequest request){
        if (request.getPostType().equals("BUSINESS_SCHEDULE")) {
            var basePost = buildBasePost(request);
            basePost.setPostType(PostType.BUSINESS_SCHEDULE);
            return postMapper.toScheduleResponse(postRepository.save(basePost));
        } else {
            var basePost = buildBasePost(request);
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
            return postMapper.toTravelItineraryResponse(travelItineraryRepository.save(travelItinerary));
        }
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

    @Transactional
    public PageResponse<ScheduleResponse> getMyPosts(int page, int size, String type){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<? extends Post> pageData;
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())) {
            pageData = postRepository.findAllByUserId(jwt.getClaim("userId"), pageable);
        } else {
            pageData = travelItineraryRepository.findAllByUserId(jwt.getClaim("userId"), pageable);
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
        if (type.equals(PostType.BUSINESS_SCHEDULE.name())){
            var schedule = postRepository.findById(id).orElseThrow(() ->
                    new AppException(ErrorCode.BUSINESS_SCHEDULE_NOT_EXISTED));
            postMapper.updateBusinessSchedule(schedule, request);

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
//
//    @Scheduled(fixedDelayString = "${jwt.auto-update-status}")
//    @Transactional
//    public void listPostValid() {
//       var list = postRepository.findAllByStatusValid(List.of("Up coming", "On going"));
//       list.forEach((item) -> {
//           String currentStatus = calculateStatus(item.getStartTime(), item.getEndTime(), LocalDateTime.now());
//           String response = " is in progress!";
//           if(!currentStatus.equals(item.getStatus())){
//               item.setStatus(currentStatus);
//               postRepository.save(item);
//               response = (currentStatus.equals("Completed") ? " has been completed!" : response);
//               messagingTemplate.convertAndSendToUser(123, 123,item.getTitle() + response);
//           }
//       });
//    }
}
