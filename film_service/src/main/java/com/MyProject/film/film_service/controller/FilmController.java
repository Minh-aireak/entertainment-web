package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.FilmRequest;
import com.MyProject.film.film_service.dto.request.RatingRequest;
import com.MyProject.film.film_service.dto.response.FilmAggregateResponse;
import com.MyProject.film.film_service.dto.response.FilmDetailResponse;
import com.MyProject.film.film_service.dto.response.FilmResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmCategory;
import com.MyProject.film.film_service.enums.FilmSortField;
import com.MyProject.film.film_service.enums.Genre;
import com.MyProject.film.film_service.service.FilmService;
import org.springframework.data.domain.Sort;
import com.MyProject.film.film_service.service.FilmApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FilmController {
    FilmService filmService;
    FilmApiRateLimitService filmApiRateLimitService;

    @PostMapping
    @RolesAllowed("ADMIN")
    @RateLimiter(name = "filmApi")
    public ApiResponse<FilmResponse> createFilm(@RequestBody @Valid FilmRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkCreateFilm(userId);
        return ApiResponse.<FilmResponse>builder()
                .result(filmService.createFilm(request))
                .build();
    }

    @GetMapping
    @RateLimiter(name = "filmApi")
    public ApiResponse<PageResponse<FilmSummaryResponse>> getPageFilms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilms(userId);
        return ApiResponse.<PageResponse<FilmSummaryResponse>>builder()
                .result(filmService.getPageFilms(page, size))
                .build();
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    @RateLimiter(name = "filmApi")
    public ApiResponse<FilmResponse> updateFilm(@PathVariable String id, @RequestBody @Valid FilmRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkCreateFilm(userId);
        return ApiResponse.<FilmResponse>builder()
                .result(filmService.updateFilm(id, request))
                .build();
    }

    @GetMapping("/aggregate")
    @RateLimiter(name = "filmApi")
    public ApiResponse<FilmAggregateResponse> getAggregateFilms() {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilms(userId);
        return ApiResponse.<FilmAggregateResponse>builder()
                .result(filmService.getAggregateFilms())
                .build();
    }

    @GetMapping("/{id}/aggregate")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<FilmDetailResponse> getFilmAggregate(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilmDetail(userId);
        return ApiResponse.<FilmDetailResponse>builder()
                .result(filmService.getFilm(id))
                .build();
    }

    @GetMapping("/now-playing")
    @RateLimiter(name = "filmApi")
    public ApiResponse<PageResponse<FilmSummaryResponse>> getNowPlayingFilms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilms(userId);
        return ApiResponse.<PageResponse<FilmSummaryResponse>>builder()
                .result(filmService.getNowPlayingFilms(page, size))
                .build();
    }

    @GetMapping("/top-rated")
    @RateLimiter(name = "filmApi")
    public ApiResponse<List<FilmSummaryResponse>> getTopRatedFilms(
            @RequestParam(defaultValue = "5") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilms(userId);
        return ApiResponse.<List<FilmSummaryResponse>>builder()
                .result(filmService.getTopRatedFilms(limit))
                .build();
    }

    @GetMapping("/browse")
    @RateLimiter(name = "filmApi")
    public ApiResponse<PageResponse<FilmSummaryResponse>> browseFilms(
            @RequestParam FilmCategory category,
            @RequestParam(required = false) Country country,
            @RequestParam(required = false) Genre genre,
            @RequestParam(defaultValue = "LAST_UPDATE") FilmSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFilms(userId);
        return ApiResponse.<PageResponse<FilmSummaryResponse>>builder()
                .result(filmService.browseFilms(category, country, genre, sortBy, sortDir, page, size))
                .build();
    }

    @GetMapping("/search")
    @RateLimiter(name = "filmApi")
    public ApiResponse<List<FilmSummaryResponse>> searchFilms(@RequestParam String title) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkSearchFilms(userId);
        return ApiResponse.<List<FilmSummaryResponse>>builder()
                .result(filmService.searchFilms(title))
                .build();
    }

    @PostMapping("/{id}/rating")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<Integer> rateFilm(
            @PathVariable String id,
            @RequestBody RatingRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkFilmRating(userId, id);
        return ApiResponse.<Integer>builder()
                .result(filmService.rateFilm(id, request))
                .build();
    }
}
