package com.MyProject.socket_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Mirrors room-service's RoomPlaybackChangedEvent - published on Kafka topic
 *  "room.playback.updated", broadcast here as WebSocket event "room:playback". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomPlaybackChangedEvent {
    String roomId;
    String action;
    boolean playing;
    double positionSeconds;
    double playbackRate;
    String episodeId;
    String actorUserId;
    Instant at;
}
