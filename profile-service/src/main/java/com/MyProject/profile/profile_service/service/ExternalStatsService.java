package com.MyProject.profile.profile_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalStatsService {

    private final ExternalStatsClient externalStatsClient;

    @Async("asyncExecutor")
    public CompletableFuture<Integer> getPostCount() {
        return CompletableFuture.completedFuture(externalStatsClient.fetchPostCount());
    }

    @Async("asyncExecutor")
    public CompletableFuture<Integer> getFriendCount() {
        return CompletableFuture.completedFuture(externalStatsClient.fetchFriendCount());
    }
}
