package com.MyProject.room_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "room_messages")
public class RoomMessage {
    @Id
    String id;

    @Indexed
    String roomId;

    String senderId;
    String senderName;
    String senderAvatar;
    String content;

    // TTL index - room chat is ephemeral, auto-purged 72h after creation (no manual
    // OutboxCleanupJob-style cron needed for this collection).
    @CreatedDate
    @Indexed(expireAfterSeconds = 259200)
    Instant createdAt;
}
