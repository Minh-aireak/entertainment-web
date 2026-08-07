package com.MyProject.room_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Broadcast to the room's WebSocket room ("room:closed") when the room ends. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomClosedEvent {
    String roomId;
    String reason;
    Instant closedAt;
}
