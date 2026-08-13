package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.event.NotificationEvent;
import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
import com.MyProject.film.film_service.dto.response.EpisodeSummaryResponse;
import com.MyProject.film.film_service.dto.response.FileResponse;
import com.MyProject.film.film_service.entity.Episode;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.FilmFollow;
import com.MyProject.film.film_service.entity.Outbox;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.EpisodeMapper;
import com.MyProject.film.film_service.repository.mysql.EpisodeRepository;
import com.MyProject.film.film_service.repository.mysql.FilmFollowRepository;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EpisodeService {
    EpisodeRepository episodeRepository;
    FilmRepository filmRepository;
    FilmFollowRepository filmFollowRepository;
    OutboxRepository outboxRepository;
    EpisodeMapper episodeMapper;
    ObjectMapper objectMapper;
    FilmService filmService;
    FileClient fileClient;

    @Transactional(readOnly = true)
    public List<EpisodeResponse> getEpisodesByFilm(String filmId) {
        if (!filmRepository.existsById(filmId)) {
            throw new AppException(ErrorCode.FILM_NOT_FOUND);
        }

        return episodeRepository.findByFilm_IdOrderBySeasonNumberAscEpisodeNumberAsc(filmId).stream()
                .map(episodeMapper::toEpisodeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EpisodeSummaryResponse> getEpisodesPage(int page, int size, String filmId, String title) {
        String normalizedTitle = (title == null || title.isBlank()) ? null : title.trim();
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Episode> pageData = episodeRepository.searchEpisodes(filmId, normalizedTitle, pageable);

        return PageResponse.<EpisodeSummaryResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream().map(episodeMapper::toEpisodeSummaryResponse).toList())
                .build();
    }

    @Transactional
    public EpisodeResponse createEpisode(EpisodeRequest request) {
        Film film = filmRepository.findById(request.getFilmId())
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));

        if (request.getVideoFileId() != null && !request.getVideoFileId().isBlank()) {
            request.setDurationMinutes(getVideoDurationMinutes(request.getVideoFileId()));
        }
        
        Episode episode = episodeMapper.toEpisode(request);
        episode.setFilm(film);
        
        var savedEpisode = episodeRepository.save(episode);
        episodeRepository.flush();
        syncFilmEpisodeCount(film);

        // Notify followers
        notifyFollowers(film, savedEpisode);
        
        return episodeMapper.toEpisodeResponse(savedEpisode);
    }

    private void notifyFollowers(Film film, Episode episode) {
        List<FilmFollow> followers = filmFollowRepository.findAllByFilmId(film.getId());
        
        if (followers.isEmpty()) {
            return;
        }

        List<String> followerIds = followers.stream()
                .map(FilmFollow::getUserId)
                .collect(Collectors.toList());

        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification("NEW_EPISODE")
                .userIdSender(SecurityUtils.getCurrentUserId())
                .toUserIds(followerIds)
                .filmTitle(film.getTitle())
                .build();

        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(film.getId())
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(notificationEvent))
                    .build());
            log.info("Saved outbox notification for new episode of film: {}", film.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event for outbox", e);
        }
    }

    @Transactional
    public EpisodeResponse updateEpisode(String id, EpisodeRequest request) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EPISODE_NOT_FOUND));
        
        Film oldFilm = episode.getFilm();
        Film targetFilm = oldFilm;

        if (request.getFilmId() != null && !request.getFilmId().equals(oldFilm.getId())) {
            targetFilm = filmRepository.findById(request.getFilmId())
                    .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));
        }

        boolean replacesVideo = request.getVideoFileId() != null
                && !request.getVideoFileId().isBlank()
                && !Objects.equals(request.getVideoFileId(), episode.getVideoFileId());
        if (replacesVideo) {
            request.setDurationMinutes(getVideoDurationMinutes(request.getVideoFileId()));
        } else {
            // Duration belongs to the stored video and cannot be edited independently.
            request.setVideoFileId(episode.getVideoFileId());
            request.setDurationMinutes(episode.getDurationMinutes());
        }
        
        episodeMapper.updateEpisode(episode, request);
        episode.setFilm(targetFilm);

        Episode savedEpisode = episodeRepository.save(episode);
        episodeRepository.flush();
        syncFilmEpisodeCount(oldFilm);
        if (!Objects.equals(targetFilm.getId(), oldFilm.getId())) {
            syncFilmEpisodeCount(targetFilm);
        }

        return episodeMapper.toEpisodeResponse(savedEpisode);
    }

    private void syncFilmEpisodeCount(Film film) {
        int latestEpisodeNumber = episodeRepository.findMaxEpisodeNumberByFilmId(film.getId()).orElse(0);
        film.setEpisodeCount(latestEpisodeNumber);
        filmRepository.save(film);
        filmService.syncFilmToElasticsearch(film);
        filmService.invalidateFilmCaches(film.getId());
    }

    private int getVideoDurationMinutes(String videoFileId) {
        ApiResponse<FileResponse> response = fileClient.getFileInfo(videoFileId);
        Long durationSeconds = response != null && response.getResult() != null
                ? response.getResult().getDuration()
                : null;

        if (durationSeconds == null || durationSeconds < 0) {
            throw new AppException(ErrorCode.VIDEO_DURATION_UNAVAILABLE);
        }

        return Math.toIntExact(durationSeconds / 60);
    }

    @Transactional
    public void deleteEpisode(String id) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EPISODE_NOT_FOUND));
        Film film = episode.getFilm();
        
        episodeRepository.delete(episode);
        episodeRepository.flush();
        syncFilmEpisodeCount(film);
    }
}
