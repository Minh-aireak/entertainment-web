package com.MyProject.film.film_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EpisodeEvent {
    String filmId;
    int seasonNumber;
    int episodeNumber;
    String title;
    String videoUrl;
    int durationMinutes;
}
