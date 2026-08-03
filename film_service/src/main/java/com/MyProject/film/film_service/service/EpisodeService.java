package com.MyProject.film.film_service.service;

import com.MyProject.film.film_service.dto.event.NotificationEvent;
import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
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
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Transactional(readOnly = true)
    public List<EpisodeResponse> getEpisodesByFilm(String filmId) {
        if (!filmRepository.existsById(filmId)) {
            throw new AppException(ErrorCode.FILM_NOT_FOUND);
        }

        return episodeRepository.findByFilm_IdOrderBySeasonNumberAscEpisodeNumberAsc(filmId).stream()
                .map(episodeMapper::toEpisodeResponse)
                .toList();
    }

    @Transactional
    public EpisodeResponse createEpisode(EpisodeRequest request) {
        Film film = filmRepository.findById(request.getFilmId())
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));
        
        Episode episode = episodeMapper.toEpisode(request);
        episode.setFilm(film);
        
        var savedEpisode = episodeRepository.save(episode);

        // Update film's episode count
        film.setEpisodeCount(film.getEpisodeCount() + 1);
        filmRepository.save(film);
        filmService.syncFilmToElasticsearch(film);

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
        
        episodeMapper.updateEpisode(episode, request);
        
        if (request.getFilmId() != null && !request.getFilmId().equals(oldFilm.getId())) {
            Film newFilm = filmRepository.findById(request.getFilmId())
                    .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));
            // Decrement old film's count
            oldFilm.setEpisodeCount(Math.max(0, oldFilm.getEpisodeCount() - 1));
            filmRepository.save(oldFilm);
            filmService.syncFilmToElasticsearch(oldFilm);
            // Increment new film's count
            newFilm.setEpisodeCount(newFilm.getEpisodeCount() + 1);
            filmRepository.save(newFilm);
            filmService.syncFilmToElasticsearch(newFilm);
            
            episode.setFilm(newFilm);
        }
        
        return episodeMapper.toEpisodeResponse(episodeRepository.save(episode));
    }

    @Transactional
    public void deleteEpisode(String id) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.EPISODE_NOT_FOUND));
        Film film = episode.getFilm();
        
        episodeRepository.delete(episode);
        
        // Update film's episode count
        film.setEpisodeCount(Math.max(0, film.getEpisodeCount() - 1));
        filmRepository.save(film);
        filmService.syncFilmToElasticsearch(film);
    }
}
