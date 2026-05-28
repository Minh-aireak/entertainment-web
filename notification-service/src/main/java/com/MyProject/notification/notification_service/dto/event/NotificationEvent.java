package com.MyProject.notification.notification_service.dto.event;

import com.MyProject.notification.notification_service.entity.TypeNotification;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationEvent {
    TypeNotification typeNotification;
    String userIdSender;
    List<String> toUserIds;
}
