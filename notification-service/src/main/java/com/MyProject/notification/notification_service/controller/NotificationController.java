package com.MyProject.notification.notification_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.notification.notification_service.dto.response.NotificationResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.notification.notification_service.service.NotificationService;
import com.MyProject.notification.notification_service.service.NotificationApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    NotificationService notificationService;
    NotificationApiRateLimitService notificationApiRateLimitService;

    @GetMapping("/my-notifications")
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationApiRateLimitService.checkGetMyNotifications(userId);
        return ApiResponse.<PageResponse<NotificationResponse>>builder()
                .result(notificationService.getMyNotifications(page, size))
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationApiRateLimitService.checkGetUnreadCount(userId);
        return ApiResponse.<Long>builder()
                .result(notificationService.getUnreadCount())
                .build();
    }

    @PutMapping("/mark-all-as-read")
    public ApiResponse<Void> markAllAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationApiRateLimitService.checkMarkAllAsRead(userId);
        notificationService.markAllAsRead();
        return ApiResponse.<Void>builder()
                .build();
    }
}
