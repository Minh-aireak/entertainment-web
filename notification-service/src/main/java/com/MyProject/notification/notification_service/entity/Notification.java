package com.MyProject.notification.notification_service.entity;

import com.MyProject.common.entity.TypeNotification;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "notifications")
public class Notification {
    @Id
    String id;
    TypeNotification type;
    String userIdSender;

    @Indexed
    List<String> toUserIds;
    Map<String, LocalDateTime> recipientReadMap;
    String message;
    LocalDateTime createdAt;
}
