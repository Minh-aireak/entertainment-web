package com.MyProject.room_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRoomRequest {
    String filmId;
    String episodeId;
    String name;
    boolean publicRoom;
    List<String> inviteeUserIds;
}
