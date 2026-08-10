package com.MyProject.post.post_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.post.post_service.dto.response.FileInfoResponse;
import com.MyProject.post.post_service.repository.httpclient.FileClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostFileExternalService {
    private final FileClient fileClient;
    private static final int MAX_RESOLVE_LIMIT = 30;

    @CircuitBreaker(name = "fileService", fallbackMethod = "resolveUrlsFallback")
    @RateLimiter(name = "fileService")
    @Retry(name = "fileService")
    public Map<String, String> resolvePresignedUrls(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return Collections.emptyMap();
        List<String> limited = fileIds.size() > MAX_RESOLVE_LIMIT
                ? fileIds.subList(0, MAX_RESOLVE_LIMIT)
                : fileIds;
        Map<String, String> result = new HashMap<>();
        for (String fileId : limited) {
            if (fileId == null || fileId.isBlank()) continue;
            try {
                ApiResponse<FileInfoResponse> resp = fileClient.getFileInfo(fileId);
                if (resp != null && resp.getResult() != null && resp.getResult().getUrl() != null) {
                    result.put(fileId, resp.getResult().getUrl());
                }
            } catch (Exception e) {
                log.warn("Failed to resolve file id={}", fileId, e);
            }
        }
        return result;
    }

    public Map<String, String> resolveUrlsFallback(List<String> fileIds, Throwable throwable) {
        log.warn("Falling back to empty url map while resolving fileIds (count={}): {}",
                fileIds == null ? 0 : fileIds.size(), throwable.getMessage());
        return Collections.emptyMap();
    }
}
