package com.MyProject.room_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/** Emitted straight onto the shared "notification" Kafka topic - field names must match
 *  socket-service's NotificationSocket record exactly, since that's what deserializes this
 *  payload on the consuming side. Intentionally realtime-only: unlike friend/comment
 *  notifications this is not persisted into notification-service's history collection. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomInviteNotificationEvent {
    String displayNameSender;
    String avatarSender;
    String type;
    String title;
    String content;
    List<String> toUserIds;
}
