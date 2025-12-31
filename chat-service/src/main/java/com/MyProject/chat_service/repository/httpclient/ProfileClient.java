package com.MyProject.chat_service.repository.httpclient;

import com.MyProject.chat_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.chat_service.dto.response.ApiResponse;
import com.MyProject.common_dto.event.dto.request.BulkUserProfileRequest;
import com.MyProject.common_dto.event.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "chat-profile-service", url = "${app.services.profile.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface ProfileClient {
    @GetMapping(value = "/internal/user-profile/{userId}")
    ApiResponse<UserProfileResponse> getUserProfile(@PathVariable String userId);

    @PostMapping(value = "/internal/bulk-user-profiles")
    ApiResponse<List<UserProfileResponse>> getBulkUserProfiles(@RequestBody BulkUserProfileRequest request);
}
