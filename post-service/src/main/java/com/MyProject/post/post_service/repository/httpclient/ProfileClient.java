package com.MyProject.post.post_service.repository.httpclient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.post.post_service.configuration.AuthenticationRequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-profile-service", url = "${app.services.profile.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface ProfileClient {
    @GetMapping(value = "/internal/user-profile/{userId}")
    ApiResponse<UserProfileResponse> getProfile(@PathVariable String userId);
}
