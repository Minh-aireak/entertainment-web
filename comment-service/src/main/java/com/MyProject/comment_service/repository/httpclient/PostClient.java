package com.MyProject.comment_service.repository.httpclient;

import com.MyProject.comment_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.common.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "comment-post-service", url = "${app.services.post.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface PostClient {
    @GetMapping(value = "/posts/internal/{id}/owner")
    ApiResponse<String> getPostOwner(@PathVariable("id") String postId);
}
