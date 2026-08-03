package com.MyProject.chat_service.service;

import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatProfileExternalService {

    private final ProfileClient profileClient;

    @CircuitBreaker(name = "profileService", fallbackMethod = "getBulkUserProfilesForApiFallback")
    @RateLimiter(name = "profileService")
    @Retry(name = "profileService")
    public Map<String, UserProfileResponse> getBulkUserProfilesForApi(Set<String> userIds) {
        return doGetBulkUserProfiles(userIds);
    }

    public Map<String, UserProfileResponse> getBulkUserProfilesForInternal(Set<String> userIds) {
        return doGetBulkUserProfiles(userIds);
    }

    private Map<String, UserProfileResponse> doGetBulkUserProfiles(Set<String> userIds) {
        var response = profileClient.getBulkUserProfiles(
                BulkUserProfileRequest.builder()
                        .userIds(userIds)
                        .build()
        );

        return response != null && response.getResult() != null
                ? response.getResult()
                : Collections.emptyMap();
    }

    public Map<String, UserProfileResponse> getBulkUserProfilesForApiFallback(Set<String> userIds, Throwable throwable) {
        log.warn("Fallback triggered while fetching profiles for chat-service users {}: {}",
                userIds,
                throwable.getMessage());
        return Collections.emptyMap();
    }
}
