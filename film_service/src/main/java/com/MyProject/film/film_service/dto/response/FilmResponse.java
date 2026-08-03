package com.MyProject.film.film_service.dto.response;

import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmResponse {
    String id;
    String title;
    String description;
    String thumbnailUrl;
    String trailerUrl;
    int durationMinutes;
    Instant releaseDate;
    Instant lastUpdate;
    Boolean series;
    double averageRating;
    int ratingCount;
    int followCount;
    int season;
    FilmStatus status;
    DirectorResponse director;
    Country country;
    Set<Genre> genres;
    List<FilmCastResponse> casts;
}
