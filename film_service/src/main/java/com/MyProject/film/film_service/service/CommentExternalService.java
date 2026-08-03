package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.response.CommentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentExternalService {

    private final CommentExternalClient commentExternalClient;

    @Async("asyncExecutor")
    public CompletableFuture<PageResponse<CommentResponse>> getComments(String filmId, int page, int size) {
        return CompletableFuture.completedFuture(commentExternalClient.fetchComments(filmId, page, size));
    }
}
