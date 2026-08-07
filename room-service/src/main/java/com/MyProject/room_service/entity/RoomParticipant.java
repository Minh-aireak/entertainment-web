package com.MyProject.room_service.entity;

import com.MyProject.room_service.enums.ParticipantRole;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "room_participants")
@CompoundIndex(name = "room_user_idx", def = "{ 'roomId': 1, 'userId': 1 }", unique = true)
public class RoomParticipant {
    @Id
    String id;

    String roomId;
    String userId;
    ParticipantRole role;
    Instant joinedAt;
}
