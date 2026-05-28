package com.MyProject.notification.notification_service.repository;

import java.time.LocalDateTime;

public interface NotificationRepositoryCustom {
    long countUnreadByUserId(String userId);
    void markAllAsRead(String userId, LocalDateTime now);
}
