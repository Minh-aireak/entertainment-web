package com.MyProject.room_service.dto.response;

import com.MyProject.room_service.enums.ParticipantRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomParticipantResponse {
    String userId;
    String displayName;
    String avatar;
    ParticipantRole role;
    Instant joinedAt;
}
