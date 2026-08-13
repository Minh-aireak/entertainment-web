package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.film.film_service.dto.request.WatchProgressRequest;
import com.MyProject.film.film_service.dto.response.WatchProgressResponse;
import com.MyProject.film.film_service.service.WatchProgressService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/watch-progress")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WatchProgressController {
    WatchProgressService watchProgressService;

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<Void> upsertProgress(@RequestBody WatchProgressRequest request) {
        watchProgressService.upsertProgress(request);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<List<WatchProgressResponse>> getMyContinueWatching() {
        return ApiResponse.<List<WatchProgressResponse>>builder()
                .result(watchProgressService.getMyContinueWatching())
                .build();
    }

    @DeleteMapping("/{filmId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<Void> removeProgress(@PathVariable String filmId) {
        watchProgressService.removeProgress(filmId);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{filmId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "filmApi")
    public ApiResponse<WatchProgressResponse> getProgressForFilm(@PathVariable String filmId) {
        return ApiResponse.<WatchProgressResponse>builder()
                .result(watchProgressService.getProgressForFilm(filmId))
                .build();
    }
}
