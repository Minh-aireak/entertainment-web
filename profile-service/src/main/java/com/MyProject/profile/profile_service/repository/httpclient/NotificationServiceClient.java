package com.MyProject.profile.profile_service.repository.httpClient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.profile.profile_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.profile.profile_service.dto.response.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "notification-service-client", url = "${app.services.notification.url}",
        configuration = { AuthenticationRequestInterceptor.class})
public interface NotificationServiceClient {

    @GetMapping(value = "/notifications/my-notifications", consumes = "application/json")
    ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size);

}
