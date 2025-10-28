package com.MyProject.chat_service.repository.httpclient;

import com.MyProject.chat_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.chat_service.dto.response.ApiResponse;
import com.MyProject.chat_service.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "chat-profile-service", url = "${app.services.profile.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface ProfileClient {
    @GetMapping(value = "/internal/user-profile/{userId}")
    ApiResponse<UserProfileResponse> getUserProfile(@PathVariable String userId);
}
