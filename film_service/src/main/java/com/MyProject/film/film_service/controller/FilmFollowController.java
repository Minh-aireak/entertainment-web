package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.service.FilmFollowService;
import com.MyProject.film.film_service.service.FilmApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FilmFollowController {
    FilmFollowService filmFollowService;
    FilmApiRateLimitService filmApiRateLimitService;

    @PostMapping("/{filmId}/{action}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<Void> processFollowAction(
            @PathVariable String filmId,
            @PathVariable String action) {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkFilmFollow(userId, filmId);
        filmFollowService.processFollowAction(filmId, action);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<List<FilmSummaryResponse>> getMyFollowedFilms() {
        String userId = SecurityUtils.getCurrentUserId();
        filmApiRateLimitService.checkReadFollowedFilms(userId);
        return ApiResponse.<List<FilmSummaryResponse>>builder()
                .result(filmFollowService.getMyFollowedFilms())
                .build();
    }
}
