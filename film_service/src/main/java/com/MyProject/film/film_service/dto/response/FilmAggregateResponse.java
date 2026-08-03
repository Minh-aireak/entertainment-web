package com.MyProject.film.film_service.dto.response;

import com.MyProject.common.dto.response.PageResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmAggregateResponse {
    PageResponse<FilmSummaryResponse> topHotFilms;
    PageResponse<FilmSummaryResponse> latestFilms;
}
