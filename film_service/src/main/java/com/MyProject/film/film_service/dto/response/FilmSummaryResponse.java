package com.MyProject.film.film_service.dto.response;

import com.MyProject.film.film_service.enums.FilmStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmSummaryResponse {
    String id;
    String title;
    String thumbnailUrl;
    double averageRating;
    int ratingCount;
    int followCount;
    int episodeCount;
    int season;
    FilmStatus status;
    Instant lastUpdate;
}
