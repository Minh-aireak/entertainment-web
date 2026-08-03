package com.MyProject.film.film_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmFollowResponse {
    String id;
    String userId;
    FilmResponse film;
    Instant createdAt;
}
