package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.repository.httpClient.FriendServiceClient;
import com.MyProject.profile.profile_service.repository.httpClient.PostServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalStatsClient {

    private final PostServiceClient postServiceClient;
    private final FriendServiceClient friendServiceClient;

    @CircuitBreaker(name = "postService", fallbackMethod = "fetchPostCountFallback")
    @Retry(name = "postService")
    public Integer fetchPostCount() {
        log.info("Fetching post count from remote service...");
        var result = postServiceClient.countMyPosts();
        return result != null && result.getResult() != null ? result.getResult() : 0;
    }

    @CircuitBreaker(name = "friendService", fallbackMethod = "fetchFriendCountFallback")
    @Retry(name = "friendService")
    public Integer fetchFriendCount() {
        log.info("Fetching friend count from remote service...");
        var result = friendServiceClient.countMyFriends();
        return result != null && result.getResult() != null ? result.getResult() : 0;
    }

    public Integer fetchPostCountFallback(Throwable ex) {
        log.error("Fallback for post service. Reason: {}", ex.getMessage());
        return 0;
    }

    public Integer fetchFriendCountFallback(Throwable ex) {
        log.error("Fallback for friend service. Reason: {}", ex.getMessage());
        return 0;
    }
}
