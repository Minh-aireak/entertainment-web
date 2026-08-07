package com.MyProject.film.film_service.dto.response;

import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmSummaryResponse {
    String id;
    String title;
    String thumbnailUrl;
    String thumbnailFileId;
    double averageRating;
    int ratingCount;
    int followCount;
    int episodeCount;
    int season;
    FilmStatus status;
    Instant lastUpdate;
    Boolean series;
    Country country;
    Set<Genre> genres;
}
