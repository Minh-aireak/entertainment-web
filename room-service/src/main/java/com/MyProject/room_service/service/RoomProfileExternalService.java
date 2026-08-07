package com.MyProject.room_service.service;

import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.repository.httpclient.ProfileClient;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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
public class RoomProfileExternalService {

    private final ProfileClient profileClient;

    @CircuitBreaker(name = "profileService", fallbackMethod = "getBulkUserProfilesFallback")
    @RateLimiter(name = "profileService")
    @Retry(name = "profileService")
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
        if (throwable instanceof RequestNotPermitted) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        log.warn("Fallback triggered while fetching profiles for room-service users {}: {}",
                userIds,
                throwable.getMessage());
        return Collections.emptyMap();
    }
}
