package com.MyProject.film.film_service.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilmSortField {
    LAST_UPDATE("lastUpdate"),
    AVERAGE_RATING("averageRating"),
    FOLLOW_COUNT("followCount"),
    RELEASE_DATE("releaseDate");

    private final String fieldName;
}
