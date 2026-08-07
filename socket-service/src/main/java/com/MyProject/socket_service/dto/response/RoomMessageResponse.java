package com.MyProject.socket_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Mirrors room-service's RoomMessageResponse - published as-is (no eventType wrapper) on
 *  Kafka topic "room.message.created", broadcast here as WebSocket event "room:message". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomMessageResponse {
    String id;
    String roomId;
    String senderId;
    String senderName;
    String senderAvatar;
    String content;
    Instant createdAt;
}
