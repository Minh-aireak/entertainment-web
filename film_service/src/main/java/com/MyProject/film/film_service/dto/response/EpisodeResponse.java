package com.MyProject.film.film_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EpisodeResponse {
    String id;
    int seasonNumber;
    int episodeNumber;
    String title;
    String videoFileId;
    int durationMinutes;
    String filmId;
}
