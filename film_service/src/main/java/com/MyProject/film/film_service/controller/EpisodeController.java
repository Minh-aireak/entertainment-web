package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
import com.MyProject.film.film_service.service.EpisodeService;
import jakarta.annotation.security.RolesAllowed;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/episodes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EpisodeController {
    EpisodeService episodeService;

    @GetMapping("/film/{filmId}")
    public ApiResponse<List<EpisodeResponse>> getEpisodesByFilm(@PathVariable String filmId) {
        return ApiResponse.<List<EpisodeResponse>>builder()
                .result(episodeService.getEpisodesByFilm(filmId))
                .build();
    }

    @PostMapping
    @RolesAllowed("ADMIN")
    public ApiResponse<EpisodeResponse> createEpisode(@RequestBody EpisodeRequest request) {
        return ApiResponse.<EpisodeResponse>builder()
                .result(episodeService.createEpisode(request))
                .build();
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<EpisodeResponse> updateEpisode(@PathVariable String id, @RequestBody EpisodeRequest request) {
        return ApiResponse.<EpisodeResponse>builder()
                .result(episodeService.updateEpisode(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<Void> deleteEpisode(@PathVariable String id) {
        episodeService.deleteEpisode(id);
        return ApiResponse.<Void>builder().build();
    }
}
