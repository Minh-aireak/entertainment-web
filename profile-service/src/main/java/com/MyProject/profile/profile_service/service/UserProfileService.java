package com.MyProject.profile.profile_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.request.ProfileSuggestionRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.profile.profile_service.document.UserProfileDoc;
import com.MyProject.profile.profile_service.dto.event.ProfileSearchUpdatedEvent;
import com.MyProject.profile.profile_service.dto.event.ProfileSocketUpdatedEvent;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.entity.Outbox;
import com.MyProject.profile.profile_service.entity.UserProfile;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.mapper.UserProfileMapper;
import com.MyProject.profile.profile_service.repository.mongo.OutboxRepository;
import com.MyProject.profile.profile_service.repository.elasticsearch.UserProfileElasticRepository;
import com.MyProject.profile.profile_service.repository.mongo.UserProfileRepository;
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

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {
    UserProfileRepository userProfileRepository;
    UserProfileElasticRepository userProfileElasticRepository;
    UserProfileMapper userProfileMapper;
    RedisService redisService;
    OutboxRepository outboxRepository;
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private void saveToOutbox(String userId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(userId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
            log.info("Saved to outbox: {}", topic);
        } catch (Exception e) {
            log.error("Failed to save to outbox", e);
        }
    }

    private String getProfileKey(String userId) {
        return "profile:user:" + userId;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse createProfile(UserProfileCreationRequest request){
        String cacheKey = getProfileKey(request.getUserId());

        // Ensure the profile is only created if it doesn't exist to avoid duplicates from retries
        boolean isNew = !userProfileRepository.existsById(request.getUserId());
        UserProfile userProfile = userProfileRepository.findById(request.getUserId())
                .orElseGet(() -> userProfileMapper.toUserProfile(request));

        UserProfile savedProfile = userProfileRepository.save(userProfile);

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(savedProfile);
        // 1. Update cache FIRST
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        // 2. For new profile: index directly to Elasticsearch (no need to publish events)
        if (isNew) {
            userProfileElasticRepository.save(userProfileMapper.toUserProfileDoc(savedProfile));
        }

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request){
        String userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        // Check which fields changed before updating
        String oldDisplayName = profile.getDisplayName();
        String oldAvatar = profile.getAvatar();

        String cacheKey = getProfileKey(userId);

        userProfileMapper.update(profile, request);

        UserProfile savedProfile = userProfileRepository.save(profile);

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(savedProfile);
        // 1. Update cache FIRST - regardless of which field changed
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        // 2. Check if displayName or avatar changed - if yes, publish events
        boolean displayNameChanged = !java.util.Objects.equals(oldDisplayName, savedProfile.getDisplayName());
        boolean avatarChanged = !java.util.Objects.equals(oldAvatar, savedProfile.getAvatar());

        if (displayNameChanged || avatarChanged) {
            String eventId = java.util.UUID.randomUUID().toString();
            String version = "1.0";
            
            // Publish search sync event
            ProfileSearchUpdatedEvent searchEvent = ProfileSearchUpdatedEvent.builder()
                    .eventId(eventId)
                    .userId(savedProfile.getUserId())
                    .avatar(savedProfile.getAvatar())
                    .displayName(savedProfile.getDisplayName())
                    .username(savedProfile.getUsername())
                    .version(version)
                    .build();
            saveToOutbox(savedProfile.getUserId(), "search.sync", searchEvent);
            
            // Publish socket event
            ProfileSocketUpdatedEvent socketEvent = ProfileSocketUpdatedEvent.builder()
                    .eventId(eventId)
                    .userId(savedProfile.getUserId())
                    .version(version)
                    .build();
            saveToOutbox(savedProfile.getUserId(), "socket.events", socketEvent);
        }

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateAvatar(String avatar){
        String userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String oldAvatar = profile.getAvatar();
        if (Objects.equals(oldAvatar, avatar)) {
            return userProfileMapper.toUserProfileResponse(profile);
        }

        String cacheKey = getProfileKey(userId);

        profile.setAvatar(avatar);
        UserProfile savedProfile = userProfileRepository.save(profile);

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(savedProfile);
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        String eventId = java.util.UUID.randomUUID().toString();
        String version = "1.0";

        ProfileSearchUpdatedEvent searchEvent = ProfileSearchUpdatedEvent.builder()
                .eventId(eventId)
                .userId(savedProfile.getUserId())
                .avatar(savedProfile.getAvatar())
                .displayName(savedProfile.getDisplayName())
                .username(savedProfile.getUsername())
                .version(version)
                .build();
        saveToOutbox(savedProfile.getUserId(), "search.sync", searchEvent);

        ProfileSocketUpdatedEvent socketEvent = ProfileSocketUpdatedEvent.builder()
                .eventId(eventId)
                .userId(savedProfile.getUserId())
                .version(version)
                .build();
        saveToOutbox(savedProfile.getUserId(), "socket.events", socketEvent);

        return response;
    }

    public UserProfileResponse getMyProfile(){
        String userId = SecurityUtils.getCurrentUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String cacheKey = getProfileKey(profile.getUserId());
        UserProfileResponse cachedResponse = redisService.get(cacheKey, new TypeReference<UserProfileResponse>() {});
        if (Objects.nonNull(cachedResponse)) {
            return cachedResponse;
        }

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(profile);
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        return response;
    }

    public List<UserProfileResponse> getAllProfiles(){
        return userProfileRepository.findAll()
                .stream().map(userProfileMapper::toUserProfileResponse)
                .toList();
    }

    public PageResponse<UserProfileResponse> getAllProfiles(int page, int size){
        Sort sort = Sort.by("joinDate").ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        String currentUserId = SecurityUtils.getCurrentUserId();
        Page<UserProfile> pageData = userProfileRepository.findByUserIdNot(currentUserId, pageable);
        List<UserProfileResponse> userProfileResponses = pageData.getContent()
                .stream().map(userProfileMapper::toUserProfileResponse)
                .toList();

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(userProfileResponses)
                .build();
    }

    public PageResponse<UserProfileResponse> getSuggestionProfiles(ProfileSuggestionRequest request) {
        Sort sort = Sort.by("joinDate").ascending();
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Page<UserProfile> pageData = userProfileRepository.findByUserIdNotIn(
                request.getExcludedUserIds(),
                pageable
        );

        List<UserProfileResponse> responses = pageData.getContent().stream()
                .map(userProfileMapper::toUserProfileResponse)
                .toList();

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(request.getPage())
                .pageSize(request.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(responses)
                .build();
    }

    public Map<String, UserProfileResponse> getBulkProfiles(BulkUserProfileRequest request){
        List<UserProfile> userProfiles = userProfileRepository.findAllById(request.getUserIds());
        
        return userProfiles.stream()
                .map(userProfileMapper::toUserProfileResponse)
                .collect(Collectors.toMap(UserProfileResponse::getUserId, p -> p));
    }

    public UserProfileResponse getProfile(String userId){
        String cacheKey = getProfileKey(userId);
        UserProfileResponse cachedResponse = redisService.get(cacheKey, new TypeReference<UserProfileResponse>() {});
        if (Objects.nonNull(cachedResponse)) {
            return cachedResponse;
        }

        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(userProfile);
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        return response;
    }

    public PageResponse<UserProfileResponse> searchProfile(String displayName, int page, int size){
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<UserProfileDoc> searchResult = userProfileElasticRepository
                .searchByUsernameContaining(displayName, pageable);

        List<UserProfileResponse> responses = searchResult.getContent().stream()
                .map(doc -> UserProfileResponse.builder()
                        .userId(doc.getUserId())
                        .username(doc.getUsername())
                        .displayName(doc.getDisplayName())
                        .avatar(doc.getAvatar())
                        .build())
                .toList();

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(responses)
                .build();
    }
}
