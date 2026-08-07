package com.MyProject.room_service.repository.httpclient;

import com.MyProject.room_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.common.dto.response.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "room-film-service", url = "${app.services.film.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface FilmClient {
    @GetMapping(value = "/films/{id}/aggregate")
    ApiResponse<FilmAggregateInfo> getFilmAggregate(@PathVariable("id") String id);

    /** Deliberately minimal - only pulls the couple of fields room-service denormalizes onto
     *  Room for its list cards. ignoreUnknown = true because film_service's real
     *  FilmDetailResponse/FilmResponse carry many more fields (cast, ratings, comments...)
     *  that room-service has no use for. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class FilmAggregateInfo {
        FilmInfo film;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class FilmInfo {
        String id;
        String title;
        String thumbnailUrl;
    }
}
