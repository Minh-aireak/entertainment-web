package com.MyProject.socket_service.repository.httpclient;

import com.MyProject.socket_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "socket-profile-service", url = "${app.services.profile.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface ProfileClient {
    @PostMapping(value = "/profiles/internal/bulk-user-profiles")
    ApiResponse<Map<String, UserProfileResponse>> getBulkUserProfiles(@RequestBody BulkUserProfileRequest request);
}
