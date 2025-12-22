package com.MyProject.notification.notification_service.controller;

import com.MyProject.common_dto.event.dto.NotificationResponse;
import com.MyProject.notification.notification_service.dto.response.ApiResponse;
import com.MyProject.notification.notification_service.dto.response.PageResponse;
import com.MyProject.notification.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    NotificationService notificationService;

    @GetMapping("/my-notifications")
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<NotificationResponse>>builder()
                .result(notificationService.getMyNotifications(page, size))
                .build();
    }

//    @PostMapping("status")
//    public void createNotificationStatusChange(StatusChangeData data) {
//        notificationService.createNotification(TypeNotification.STATUS_CHANGE, data.fromUserId(), data.listUserIds(), data.message());
//    }

//    @GetMapping("/unread-count")
//    public ApiResponse<Long> getUnreadCount() {
//        return ApiResponse.<Long>builder()
//                .result(notificationService.getUnreadCount())
//                .build();
//    }

//    @PutMapping("/{notificationId}/mark-as-read")
//    public ApiResponse<Void> markAsRead(@PathVariable String notificationId) {
//        notificationService.markAsRead(notificationId);
//        return ApiResponse.<Void>builder()
//                .message("Notification marked as read")
//                .build();
//    }
//
//    @PutMapping("/mark-all-as-read")
//    public ApiResponse<Void> markAllAsRead() {
//        notificationService.markAllAsRead();
//        return ApiResponse.<Void>builder()
//                .message("All notifications marked as read")
//                .build();
//    }
}
