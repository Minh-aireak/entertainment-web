package com.MyProject.room_service.entity;

import com.MyProject.room_service.enums.RoomStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "rooms")
@CompoundIndex(
        name = "status_public_modified_idx",
        def = "{ 'status': 1, 'publicRoom': 1, 'modifiedDate': -1 }"
)
public class Room {
    @Id
    String id;

    String name;
    String hostUserId;

    String filmId;
    String filmTitle;
    String filmThumbnail;
    String episodeId;

    boolean publicRoom;
    String inviteCode;
    @Builder.Default
    Set<String> invitedUserIds = new HashSet<>();

    RoomStatus status;

    boolean playing;
    double positionSeconds;
    @Builder.Default
    double playbackRate = 1.0;
    Instant lastActionAt;

    int participantCount;
    int maxParticipants;

    @CreatedDate
    Instant createdDate;
    @LastModifiedDate
    Instant modifiedDate;
}
