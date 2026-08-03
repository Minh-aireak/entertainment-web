package com.MyProject.notification.notification_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.notification.notification_service.repository.httpclient.ProfileClient;
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
public class NotificationProfileExternalService {

    private final ProfileClient profileClient;

    @CircuitBreaker(name = "notificationProfileService", fallbackMethod = "getBulkUserProfilesFallback")
    @RateLimiter(name = "notificationProfileService")
    @Retry(name = "notificationProfileService")
    public Map<String, UserProfileResponse> getBulkUserProfiles(Set<String> userIds) {
        var response = profileClient.getBulkUserProfiles(
                BulkUserProfileRequest.builder()
                        .userIds(userIds)
                        .build()
        );

        return response != null && response.getResult() != null
                ? response.getResult()
                : Collections.emptyMap();
    }

    public Map<String, UserProfileResponse> getBulkUserProfilesFallback(Set<String> userIds, Throwable throwable) {
        log.warn("Fallback triggered while fetching notification profiles for users {}: {}",
                userIds,
                throwable.getMessage());
        return Collections.emptyMap();
    }
}
