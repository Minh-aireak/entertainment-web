package com.MyProject.profile.profile_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.profile.profile_service.document.UserProfileDoc;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null)
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private String getProfileKey(String userId) {
        return "profile:user:" + userId;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse createProfile(UserProfileCreationRequest request){
        String cacheKey = getProfileKey(request.getUserId());

        // Ensure the profile is only created if it doesn't exist to avoid duplicates from retries
        UserProfile userProfile = userProfileRepository.findById(request.getUserId())
                .orElseGet(() -> userProfileMapper.toUserProfile(request));

        UserProfile savedProfile = userProfileRepository.save(userProfile);

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(savedProfile);
        // 1. Update cache FIRST
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        // 2. Save to Outbox
        saveToOutbox((response.getUserId()), "profile.sync", userProfileMapper.toUserProfileDoc(savedProfile));

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request){
        String userId = getUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String cacheKey = getProfileKey(userId);

        userProfileMapper.update(profile, request);

        UserProfile savedProfile = userProfileRepository.save(profile);

        UserProfileResponse response = userProfileMapper.toUserProfileResponse(savedProfile);
        // 1. Update cache FIRST
        redisService.setWithExpiration(cacheKey, response, 1, TimeUnit.HOURS);

        // 2. Save to Outbox
        saveToOutbox(getUserId(), "profile.sync", userProfileMapper.toUserProfileDoc(savedProfile));

        return response;
    }

    public UserProfileResponse getMyProfile(){
        String userId = getUserId();
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String cacheKey = getProfileKey(profile.getUserId());
        UserProfileResponse cachedResponse = (UserProfileResponse) redisService.get(cacheKey);
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

        Page<UserProfile> pageData = userProfileRepository.findAll(pageable);
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

    public Map<String, UserProfileResponse> getBulkProfiles(BulkUserProfileRequest request){
        List<UserProfile> userProfiles = userProfileRepository.findAllById(request.getUserIds());
        
        return userProfiles.stream()
                .map(userProfileMapper::toUserProfileResponse)
                .collect(Collectors.toMap(UserProfileResponse::getUserId, p -> p));
    }

    public UserProfileResponse getProfile(String userId){
        String cacheKey = getProfileKey(userId);
        UserProfileResponse cachedResponse = (UserProfileResponse) redisService.get(cacheKey);
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
