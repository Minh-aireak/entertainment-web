package com.MyProject.room_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/** Emitted onto the "notification.events" Kafka topic - field names must match notification-service's
 *  NotificationEvent exactly, since that's what deserializes this payload. notification-service keeps
 *  the invite in each invitee's notification history, then pushes the realtime popup itself (it owns
 *  the wording and resolves the host's profile). eventId doubles as its idempotency key. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomInviteNotificationEvent {
    String eventId;
    String typeNotification;
    String userIdSender;
    List<String> toUserIds;
    String roomId;
    String roomName;
}
