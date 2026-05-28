package com.MyProject.notification.notification_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResponse {
    String id;
    String type;
    String userIdSender;
    String displayNameSender;
    String avatarSender;
    Boolean read;
    String message;
    String createdAt;
}


