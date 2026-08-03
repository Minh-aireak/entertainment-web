package com.MyProject.film.film_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmCreatedEvent {
    String eventId;
    String id;
    String title;
}
