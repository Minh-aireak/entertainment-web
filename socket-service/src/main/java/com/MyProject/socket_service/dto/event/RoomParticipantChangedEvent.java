package com.MyProject.socket_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Mirrors room-service's RoomParticipantChangedEvent - published on Kafka topic
 *  "room.participant.changed", broadcast here as WebSocket event "room:participants". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomParticipantChangedEvent {
    String roomId;
    String eventType;
    Participant participant;
    int participantCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Participant {
        String userId;
        String displayName;
        String avatar;
        String role;
        Instant joinedAt;
    }
}
