package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.response.CommentResponse;
import com.MyProject.film.film_service.repository.httpclient.CommentClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentExternalClient {

    private final CommentClient commentClient;

    @CircuitBreaker(name = "commentService", fallbackMethod = "getCommentsFallback")
    @Retry(name = "commentService")
    public PageResponse<CommentResponse> fetchComments(String filmId, int page, int size) {
        log.info("Fetching comments for film {} from remote service...", filmId);
        ApiResponse<PageResponse<CommentResponse>> response = commentClient.getAllComments(page, size, filmId);
        return (response != null && response.getResult() != null) ? response.getResult() : null;
    }

    public PageResponse<CommentResponse> getCommentsFallback(String filmId, int page, int size, Throwable t) {
        log.warn("Fallback triggered for comments of film {} page {}: {}", filmId, page, t.getMessage());
        return PageResponse.<CommentResponse>builder()
                .data(List.of())
                .currentPage(page)
                .pageSize(size)
                .totalElement(0)
                .totalPages(0)
                .build();
    }
}
