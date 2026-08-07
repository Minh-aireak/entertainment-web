package com.MyProject.film.film_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationEvent {
    String eventId;
    String typeNotification;
    String userIdSender;
    List<String> toUserIds;
    String filmTitle;
}
