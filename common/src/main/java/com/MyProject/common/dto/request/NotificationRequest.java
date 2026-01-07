package com.MyProject.common.dto.request;

import com.MyProject.common.entity.TypeNotification;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationRequest {
    TypeNotification typeNotification;
    String userIdSender;
    List<String> toUserIds;
    Map<String, Object> metadata;
}