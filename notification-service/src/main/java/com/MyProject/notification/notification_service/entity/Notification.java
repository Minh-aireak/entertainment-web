package com.MyProject.notification.notification_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "toUserIds_idx", def = "{'toUserIds': 1}"),
        @CompoundIndex(name = "toUserIds_createdAt_idx", def = "{'toUserIds': 1, 'createdAt': -1}")
})
public class Notification {
    @MongoId
    String id;
    TypeNotification type;
    String userIdSender;

    List<String> toUserIds;

    Map<String, LocalDateTime> recipientReadMap;

    String conversationId;

    String filmTitle;

    @Builder.Default
    Integer count = 1;

    @CreatedDate
    @Indexed
    LocalDateTime createdAt;
}
