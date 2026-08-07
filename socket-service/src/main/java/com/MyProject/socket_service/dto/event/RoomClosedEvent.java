package com.MyProject.socket_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Mirrors room-service's RoomClosedEvent - published on Kafka topic "room.closed",
 *  broadcast here as WebSocket event "room:closed". */
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
