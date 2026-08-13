package com.MyProject.room_service.dto.response;

import com.MyProject.room_service.enums.RoomStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomResponse {
    String id;
    String name;
    String hostUserId;
    String hostDisplayName;
    String hostAvatar;
    /** True when the caller of this request is the host - lets the frontend decide whether to
     *  render the player in host (interactive) or viewer (read-only) mode. */
    boolean host;
    /** True when the caller has already joined (host or viewer) - frontend skips the join call. */
    boolean participant;

    String filmId;
    String filmTitle;
    String filmThumbnail;
    String episodeId;

    boolean publicRoom;
    String inviteCode;

    RoomStatus status;

    boolean playing;
    /** Live-computed position (positionSeconds extrapolated by elapsed time if playing), in
     *  seconds - safe to seek a freshly-joined player to directly. */
    double positionSeconds;
    double playbackRate;
    Instant lastActionAt;
    /** Mirrors Room.playbackRevision - the frontend ignores any snapshot/event whose revision is
     *  not newer than (for events) or at least as new as (for snapshots) the last one it applied,
     *  so a reconnect resync race can never roll playback state backwards. */
    int playbackRevision;
    /** Short-lived signed token (see RoomSubscriptionTokenService) letting socket-service verify
     *  this caller may subscribe to this room's WebSocket channel - null for non-participants. */
    String wsToken;

    int participantCount;
    int maxParticipants;

    Instant createdDate;
}
