package com.MyProject.room_service.dto.event;

import com.MyProject.room_service.enums.PlaybackAction;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Broadcast to the room's WebSocket room ("room:playback") whenever the host changes playback
 *  state. Carries the raw state (not a pre-computed live position) plus the server timestamp
 *  "at" it was set at, so each viewer extrapolates the live position locally the same way
 *  RoomService.computeLivePositionSeconds does - see room-service PlaybackAction docs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomPlaybackChangedEvent {
    String roomId;
    PlaybackAction action;
    boolean playing;
    double positionSeconds;
    double playbackRate;
    String episodeId;
    String actorUserId;
    Instant at;
    int playbackRevision;
}
