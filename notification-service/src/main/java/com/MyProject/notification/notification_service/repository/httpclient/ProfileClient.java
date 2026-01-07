package com.MyProject.notification.notification_service.repository.httpclient;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.notification.notification_service.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping(value = "/internal/user-profile/{userId}")
    ApiResponse<UserProfileResponse> getProfile(@PathVariable String userId);
    
    @PostMapping("/internal/bulk-user-profiles")
    ApiResponse<List<UserProfileResponse>> getBulkUserProfiles(@RequestBody BulkUserProfileRequest request);
}
