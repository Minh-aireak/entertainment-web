package com.MyProject.room_service.service;

import com.MyProject.room_service.repository.httpclient.FilmClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomFilmExternalService {

    private final FilmClient filmClient;

    @CircuitBreaker(name = "filmService", fallbackMethod = "getFilmInfoFallback")
    @RateLimiter(name = "filmService")
    @Retry(name = "filmService")
    public Optional<FilmClient.FilmInfo> getFilmInfo(String filmId) {
        var response = filmClient.getFilmAggregate(filmId);
        if (response == null || response.getResult() == null || response.getResult().getFilm() == null) {
            return Optional.empty();
        }
        return Optional.of(response.getResult().getFilm());
    }

    /** Room creation must not hard-fail just because film_service is briefly unavailable - the
     *  room is still created, just without the denormalized title/thumbnail for its list card
     *  (frontend falls back to showing the raw filmId until the next successful lookup). */
    public Optional<FilmClient.FilmInfo> getFilmInfoFallback(String filmId, Throwable throwable) {
        log.warn("Fallback triggered while fetching film info for room-service filmId {}: {}",
                filmId,
                throwable.getMessage());
        return Optional.empty();
    }
}
