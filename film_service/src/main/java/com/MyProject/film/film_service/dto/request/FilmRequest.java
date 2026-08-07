package com.MyProject.film.film_service.dto.request;

import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class FilmRequest {
    @NotBlank(message = "TITLE_REQUIRED")
    String title;

    @NotBlank(message = "DESCRIPTION_REQUIRED")
    String description;

    String thumbnailUrl;
    String thumbnailFileId;
    String trailerUrl;
    int durationMinutes;
    Instant releaseDate;
    Boolean series;

    @NotEmpty(message = "DIRECTOR_REQUIRED")
    List<String> directorIds;

    int season;

    FilmStatus status;

    @NotNull(message = "COUNTRY_REQUIRED")
    Country country;

    @NotEmpty(message = "GENRES_REQUIRED")
    Set<Genre> genres;

    List<CastRequest> casts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CastRequest {
        String actorId;
        String characterName;
        int displayOrder;
    }
}
