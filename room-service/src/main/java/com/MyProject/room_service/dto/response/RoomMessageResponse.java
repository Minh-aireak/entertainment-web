package com.MyProject.room_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

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
