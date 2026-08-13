package com.MyProject.film.film_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.dto.request.WatchProgressRequest;
import com.MyProject.film.film_service.dto.response.WatchProgressResponse;
import com.MyProject.film.film_service.entity.Episode;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.WatchProgress;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.EpisodeRepository;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.MyProject.film.film_service.repository.mysql.WatchProgressRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WatchProgressService {
    // One row per (user, film): switching episodes just updates the row instead of creating a new
    // one, matching how "Continue Watching" behaves elsewhere (one card per title, not per episode).
    private static final int CONTINUE_WATCHING_LIMIT = 20;

    WatchProgressRepository watchProgressRepository;
    FilmRepository filmRepository;
    EpisodeRepository episodeRepository;
    FileClient fileClient;

    @Transactional
    public void upsertProgress(WatchProgressRequest request) {
        if (request.getFilmId() == null || request.getFilmId().isBlank()
                || request.getPositionSeconds() < 0 || request.getDurationSeconds() < 0) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        String userId = SecurityUtils.getCurrentUserId();

        Film film = filmRepository.findById(request.getFilmId())
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));

        Episode episode = null;
        if (request.getEpisodeId() != null && !request.getEpisodeId().isBlank()) {
            episode = episodeRepository.findById(request.getEpisodeId())
                    .orElseThrow(() -> new AppException(ErrorCode.EPISODE_NOT_FOUND));
        }

        WatchProgress progress = watchProgressRepository.findByUserIdAndFilmId(userId, film.getId())
                .orElseGet(() -> WatchProgress.builder().userId(userId).build());

        progress.setFilm(film);
        progress.setEpisode(episode);
        progress.setPositionSeconds(request.getPositionSeconds());
        progress.setDurationSeconds(request.getDurationSeconds());

        watchProgressRepository.save(progress);
    }

    @Transactional(readOnly = true)
    public List<WatchProgressResponse> getMyContinueWatching() {
        String userId = SecurityUtils.getCurrentUserId();
        return watchProgressRepository
                .findContinueWatching(userId, PageRequest.of(0, CONTINUE_WATCHING_LIMIT)).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeProgress(String filmId) {
        String userId = SecurityUtils.getCurrentUserId();
        watchProgressRepository.deleteByUserIdAndFilmId(userId, filmId);
    }

    // Null (not a thrown 404) when there's no saved progress - the caller just means "nothing to
    // resume", which is the normal case for a film the user hasn't started, not an error.
    @Transactional(readOnly = true)
    public WatchProgressResponse getProgressForFilm(String filmId) {
        String userId = SecurityUtils.getCurrentUserId();
        return watchProgressRepository.findByUserIdAndFilmId(userId, filmId)
                .map(this::toResponse)
                .orElse(null);
    }

    private WatchProgressResponse toResponse(WatchProgress progress) {
        Film film = progress.getFilm();
        Episode episode = progress.getEpisode();

        WatchProgressResponse response = WatchProgressResponse.builder()
                .filmId(film.getId())
                .filmTitle(film.getTitle())
                .thumbnailUrl(film.getThumbnailUrl())
                .thumbnailFileId(film.getThumbnailFileId())
                .series(film.getSeries())
                .episodeId(episode != null ? episode.getId() : null)
                .episodeNumber(episode != null ? episode.getEpisodeNumber() : null)
                .totalEpisodes(film.getEpisodeCount())
                .positionSeconds(progress.getPositionSeconds())
                .durationSeconds(progress.getDurationSeconds())
                .updatedAt(progress.getUpdatedAt())
                .build();

        return resolveThumbnail(response);
    }

    // Same reasoning as FilmFollowService.resolveThumbnail(): a thumbnailFileId points at a private
    // B2 object, so the URL has to be re-signed on every read instead of being cached on the row.
    private WatchProgressResponse resolveThumbnail(WatchProgressResponse response) {
        if (response.getThumbnailFileId() == null || response.getThumbnailFileId().isBlank()) {
            return response;
        }
        try {
            response.setThumbnailUrl(fileClient.getFileInfo(response.getThumbnailFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail file {} for film {}", response.getThumbnailFileId(), response.getFilmId(), e);
        }
        return response;
    }
}
