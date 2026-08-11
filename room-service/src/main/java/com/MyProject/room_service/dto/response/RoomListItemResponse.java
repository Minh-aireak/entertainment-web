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
public class RoomListItemResponse {
    String id;
    String name;
    String hostUserId;
    String hostDisplayName;
    String hostAvatar;

    String filmId;
    String filmTitle;
    String filmThumbnail;
    String episodeId;

    boolean publicRoom;
    RoomStatus status;

    /** Only meaningful in the GET /rooms/my response - true if the caller has already joined
     *  (host or viewer), false if this room only appears because they were invited and haven't
     *  joined yet. Always false in the public list and in the lobby broadcast payload, both of
     *  which aren't scoped to a single viewer. */
    boolean alreadyJoined;

    int participantCount;
    int maxParticipants;

    Instant createdDate;
    Instant modifiedDate;
}
