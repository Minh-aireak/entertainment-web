package com.MyProject.common_dto.event.dto.response;

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
    Boolean isRead;
    String message;
    LocalDateTime createdAt;
}


