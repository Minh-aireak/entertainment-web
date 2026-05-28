package com.MyProject.profile.profile_service.repository.httpClient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.configuration.AuthenticationRequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "post-service-client", url = "${app.services.post.url}",
        configuration = { AuthenticationRequestInterceptor.class})
public interface PostServiceClient {

    @GetMapping(value = "/posts/count")
    ApiResponse<Integer> countMyPosts();
}
