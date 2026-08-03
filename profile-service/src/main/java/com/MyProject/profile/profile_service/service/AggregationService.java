package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.dto.response.UserFullSummaryResponse;
import com.MyProject.profile.profile_service.repository.httpClient.FriendServiceClient;
import com.MyProject.profile.profile_service.repository.httpClient.PostServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private final PostServiceClient postServiceClient;
    private final FriendServiceClient friendServiceClient;
    private final UserProfileService userProfileService;
    private final Executor asyncExecutor;

    // Gọi song song 2 service
    public UserFullSummaryResponse getUserSummary() {
        CompletableFuture<Integer> totalPosts = CompletableFuture
                .supplyAsync(this::getPostCount, asyncExecutor);

        CompletableFuture<Integer> totalFriends = CompletableFuture
                .supplyAsync(this::getFriendCount, asyncExecutor);

        var response = userProfileService.getMyProfile();

        CompletableFuture.allOf(totalPosts, totalFriends).join();

        return UserFullSummaryResponse.builder()
                .userId(response.getUserId())
                .username(response.getUsername())
                .email(response.getEmail())
                .displayName(response.getDisplayName())
                .dob(response.getDob() != null ? response.getDob().toString() : null)
                .phoneNumber(response.getPhoneNumber())
                .city(response.getCity())
                .joinDate(response.getJoinDate() != null ? response.getJoinDate().toString() : null)
                .avatar(response.getAvatar())
                .totalPosts(totalPosts.join())
                .totalFriends(totalFriends.join())
                .build();
    }

    @CircuitBreaker(name = "postService", fallbackMethod = "postCountFallback")
    @TimeLimiter(name = "postService")
    public Integer getPostCount() {
        return postServiceClient.countMyPosts().getResult();
    }

    @CircuitBreaker(name = "friendService", fallbackMethod = "friendCountFallback")
    @TimeLimiter(name = "friendService")
    public Integer getFriendCount() {
        return friendServiceClient.countMyFriends().getResult();
    }

    // Fallback methods

    // Tên phải khớp fallbackMethod, thêm Throwable ở cuối
    public Integer postCountFallback(Throwable ex) {
        log.warn("Circuit open - Post service unavailable: {}", ex.getMessage());
        return null;
    }

    public Integer friendCountFallback(String userId, Throwable ex) {
        log.warn("Circuit open - Friend service unavailable: {}", ex.getMessage());
        return null;
    }
}