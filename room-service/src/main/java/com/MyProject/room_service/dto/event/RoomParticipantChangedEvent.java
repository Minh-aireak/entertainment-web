package com.MyProject.room_service.dto.event;

import com.MyProject.room_service.dto.response.RoomParticipantResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

/** Broadcast to the room's WebSocket room ("room:participants") on join/leave. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomParticipantChangedEvent {
    String roomId;
    String eventType;
    RoomParticipantResponse participant;
    int participantCount;
}
