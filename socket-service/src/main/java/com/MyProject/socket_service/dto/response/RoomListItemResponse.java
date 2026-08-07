package com.MyProject.socket_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** Mirrors room-service's RoomListItemResponse - published on Kafka topic "room.lobby.created"
 *  (public rooms only), broadcast here as WebSocket event "lobby:room-created" to everyone
 *  currently browsing the watch-together lobby, so the public room list updates live instead of
 *  needing a manual refresh. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomListItemResponse {
    String id;
    String name;
    String hostUserId;
    String hostDisplayName;
    String hostAvatar;
    String filmId;
    String filmTitle;
    String filmThumbnail;
    boolean publicRoom;
    // Always false on this broadcast payload - alreadyJoined is only meaningful per-viewer, see
    // room-service's RoomListItemResponse javadoc. Kept here purely so the field round-trips
    // through deserialization instead of being silently dropped.
    boolean alreadyJoined;
    String status;
    int participantCount;
    int maxParticipants;
    Instant createdDate;
    Instant modifiedDate;
}
