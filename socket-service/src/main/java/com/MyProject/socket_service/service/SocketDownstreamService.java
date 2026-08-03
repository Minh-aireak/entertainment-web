package com.MyProject.socket_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.socket_service.dto.response.IntrospectResponse;
import com.MyProject.socket_service.repository.httpclient.IdentityClient;
import com.MyProject.socket_service.repository.httpclient.ProfileClient;
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
public class SocketDownstreamService {

    private final IdentityClient identityClient;
    private final ProfileClient profileClient;

    @CircuitBreaker(name = "socketIdentityService", fallbackMethod = "introspectFallback")
    @RateLimiter(name = "socketIdentityService")
    @Retry(name = "socketIdentityService")
    public IntrospectResponse introspectAccessToken(String token) {
        var response = identityClient.introspect(token);
        return response != null && response.getResult() != null
                ? response.getResult()
                : IntrospectResponse.builder().valid(false).build();
    }

    public IntrospectResponse introspectFallback(String token, Throwable throwable) {
        log.warn("Fallback triggered while introspecting socket token: {}", throwable.getMessage());
        return IntrospectResponse.builder().valid(false).build();
    }

    @CircuitBreaker(name = "socketProfileService", fallbackMethod = "getBulkUserProfilesFallback")
    @RateLimiter(name = "socketProfileService")
    @Retry(name = "socketProfileService")
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
        log.warn("Fallback triggered while fetching socket profiles for users {}: {}",
                userIds,
                throwable.getMessage());
        return Collections.emptyMap();
    }
}
