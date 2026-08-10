package com.MyProject.comment_service.service;

import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.repository.httpclient.PostClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentPostExternalService {

    private final PostClient postClient;

    @CircuitBreaker(name = "postService", fallbackMethod = "getPostOwnerFallback")
    @RateLimiter(name = "postService")
    @Retry(name = "postService")
    public String getPostOwner(String postId) {
        var response = postClient.getPostOwner(postId);
        return response != null ? response.getResult() : null;
    }

    public String getPostOwnerFallback(String postId, Throwable throwable) {
        if (throwable instanceof RequestNotPermitted) {
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        log.warn("Fallback triggered while fetching post owner for postId {}: {}",
                postId,
                throwable.getMessage());
        return null;
    }
}
