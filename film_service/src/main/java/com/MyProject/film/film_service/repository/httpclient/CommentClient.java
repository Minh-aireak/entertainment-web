package com.MyProject.film.film_service.repository.httpclient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.film.film_service.dto.response.CommentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "comment-service",  url = "${app.services.comment.url}",
        configuration = AuthenticationRequestInterceptor.class)
public interface CommentClient {
    @GetMapping("/comments")
    ApiResponse<PageResponse<CommentResponse>> getAllComments(
            @RequestParam(value = "page") Integer page,
            @RequestParam(value = "size") Integer size,
            @RequestParam(value = "sourceId") String sourceId);
}
