package com.MyProject.room_service.dto.request;

import com.MyProject.room_service.enums.PlaybackAction;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaybackUpdateRequest {
    PlaybackAction action;
    Double positionSeconds;
    String episodeId;
    Double playbackRate;
}
