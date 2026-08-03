package com.MyProject.film.film_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmCastResponse {
    String id;
    ActorResponse actor;
    String characterName;
    int displayOrder;
}
