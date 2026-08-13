package com.MyProject.film.film_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WatchProgressRequest {
    String filmId;
    String episodeId;
    int positionSeconds;
    int durationSeconds;
}
