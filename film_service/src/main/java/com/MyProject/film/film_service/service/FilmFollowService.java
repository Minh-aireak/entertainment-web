package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.dto.event.FollowEvent;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.FilmFollow;
import com.MyProject.film.film_service.entity.Outbox;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.FilmFollowMapper;
import com.MyProject.film.film_service.mapper.FilmMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
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
public class FilmFollowService {
    FilmFollowRepository filmFollowRepository;
    FilmRepository filmRepository;
    FilmFollowMapper filmFollowMapper;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;
    FilmMapper filmMapper;
    RedisService redisService;
    FileClient fileClient;

    @Transactional
    public void processFollowAction(String filmId, String action) {
        if ("FOLLOW".equalsIgnoreCase(action)) {
            followFilm(filmId);
        } else if ("UNFOLLOW".equalsIgnoreCase(action)) {
            unfollowFilm(filmId);
        } else {
            // Default to toggle if action is not explicit or different
            if (isFollowing(filmId)) {
                unfollowFilm(filmId);
            } else {
                followFilm(filmId);
            }
        }
        invalidateFilmCaches(filmId);
    }

    private void invalidateFilmCaches(String filmId) {
        try {
            redisService.delete("film:detail:" + filmId);
            redisService.deletePattern("film:comments:" + filmId + ":*");
            redisService.deletePattern("film:latest:page:*");
            redisService.deletePattern("film:hot:page:*");
            log.info("Invalidated film caches for film: {}", filmId);
        } catch (Exception e) {
            log.warn("Failed to invalidate caches for film: {}", filmId, e);
        }
    }

    private void followFilm(String filmId) {
        String userId = SecurityUtils.getCurrentUserId();
        
        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));

        // 1. Update DB immediately
        if (!filmFollowRepository.existsByUserIdAndFilmId(userId, filmId)) {
            FilmFollow follow = FilmFollow.builder()
                    .userId(userId)
                    .film(film)
                    .build();
            filmFollowRepository.save(follow);
            log.info("Persisted follow record for user {} and film {}", userId, filmId);
        }
        
        // 2. Emit event via Outbox for async processing (update follow count & sync Elastic)
        emitFollowEvent(userId, filmId, "FOLLOW");
    }

    private void unfollowFilm(String filmId) {
        String userId = SecurityUtils.getCurrentUserId();
        
        // 1. Update DB immediately
        if (filmFollowRepository.existsByUserIdAndFilmId(userId, filmId)) {
            filmFollowRepository.deleteByUserIdAndFilmId(userId, filmId);
            log.info("Removed follow record for user {} and film {}", userId, filmId);
        }
        
        // 2. Emit event via Outbox for async processing (update follow count & sync Elastic)
        emitFollowEvent(userId, filmId, "UNFOLLOW");
    }

    private void emitFollowEvent(String userId, String filmId, String action) {
        FollowEvent event = FollowEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(userId)
                .filmId(filmId)
                .action(action)
                .build();
        
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(filmId)
                    .topic("film.follow")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize follow event for outbox", e);
            throw new RuntimeException("Failed to serialize follow event", e);
        }
    }



    public List<FilmSummaryResponse> getMyFollowedFilms() {
        String userId = SecurityUtils.getCurrentUserId();
        return filmFollowRepository.findAllByUserId(userId).stream()
                .map(filmFollow -> filmMapper.toFilmSummaryResponse(filmFollow.getFilm()))
                .map(this::resolveThumbnail)
                .collect(Collectors.toList());
    }

    // Phim có thumbnailFileId (upload qua file-service, bucket B2 private) chỉ lưu metadata, không
    // lưu URL vĩnh viễn - phải resolve presigned URL mới mỗi lần đọc, giống FilmService.resolveThumbnail().
    private FilmSummaryResponse resolveThumbnail(FilmSummaryResponse response) {
        if (response.getThumbnailFileId() == null || response.getThumbnailFileId().isBlank()) {
            return response;
        }
        try {
            response.setThumbnailUrl(fileClient.getFileInfo(response.getThumbnailFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail file {} for film {}", response.getThumbnailFileId(), response.getId(), e);
        }
        return response;
    }

    public boolean isFollowing(String filmId) {
        String userId = SecurityUtils.getCurrentUserId();

        return filmFollowRepository.existsByUserIdAndFilmId(userId, filmId);
    }
}
