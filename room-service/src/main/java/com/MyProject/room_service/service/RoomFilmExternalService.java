package com.MyProject.room_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.repository.httpclient.FilmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomFilmExternalService {
    private static final String EPISODES_CACHE_PREFIX = "room:episodes:film:";
    private static final Pattern FILM_NOT_FOUND_RESPONSE_CODE =
            Pattern.compile("\"code\"\\s*:\\s*8903\\b");

    private final FilmClient filmClient;
    private final RedisService redisService;

    @CircuitBreaker(name = "filmService", fallbackMethod = "getFilmInfoFallback")
    @RateLimiter(name = "filmService")
    @Retry(name = "filmService")
    public Optional<FilmClient.FilmInfo> getFilmInfo(String filmId) {
        try {
            var response = filmClient.getFilmAggregate(filmId);
            if (response == null || response.getResult() == null || response.getResult().getFilm() == null) {
                throw new AppException(ErrorCode.FILM_SERVICE_UNAVAILABLE);
            }
            return Optional.of(response.getResult().getFilm());
        } catch (FeignException.NotFound exception) {
            throw classifyNotFound(filmId, exception);
        }
    }

    /** Room creation requires a verified film. A transient downstream failure must not create a
     *  room containing an unverified id that will only fail later during CHANGE_EPISODE. */
    public Optional<FilmClient.FilmInfo> getFilmInfoFallback(String filmId, Throwable throwable) {
        if (throwable instanceof AppException appException) {
            throw appException;
        }
        log.warn("Fallback triggered while fetching film info for room-service filmId {}: {}",
                filmId,
                throwable.getMessage());
        throw new AppException(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    /** Used to validate CHANGE_EPISODE targets and to clamp positionSeconds against episode
     *  duration. Cached briefly (5 min) since updatePlayback may call this on every host action -
     *  a stale cache just means a just-added episode takes a few minutes to become selectable,
     *  which is an acceptable tradeoff against hitting film-service on every playback update. */
    @CircuitBreaker(name = "filmService", fallbackMethod = "getEpisodesByFilmFallback")
    @RateLimiter(name = "filmService")
    @Retry(name = "filmService")
    public List<FilmClient.EpisodeInfo> getEpisodesByFilm(String filmId) {
        String cacheKey = EPISODES_CACHE_PREFIX + filmId;
        try {
            List<FilmClient.EpisodeInfo> cached = redisService.get(cacheKey, new TypeReference<List<FilmClient.EpisodeInfo>>() {});
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Failed to read cached episodes for film {}", filmId, e);
        }

        List<FilmClient.EpisodeInfo> episodes = fetchEpisodesByFilm(filmId);

        // Do not cache an empty catalog: newly-added episodes should become selectable without
        // waiting for this cache's full TTL.
        if (!episodes.isEmpty()) {
            try {
                redisService.setWithExpiration(cacheKey, episodes, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Failed to cache episodes for film {}", filmId, e);
            }
        }

        return episodes;
    }

    /** Cache-only lookup for the position-clamping best-effort path - never forces a remote call
     *  (see RoomService), so a cache miss here just skips clamping rather than adding latency to
     *  every HEARTBEAT. */
    public Optional<FilmClient.EpisodeInfo> getCachedEpisode(String filmId, String episodeId) {
        try {
            List<FilmClient.EpisodeInfo> cached = redisService.get(EPISODES_CACHE_PREFIX + filmId, new TypeReference<List<FilmClient.EpisodeInfo>>() {});
            if (cached == null) return Optional.empty();
            return cached.stream().filter(e -> episodeId.equals(e.getId())).findFirst();
        } catch (Exception e) {
            log.warn("Failed to read cached episode {} for film {}", episodeId, filmId, e);
            return Optional.empty();
        }
    }

    public List<FilmClient.EpisodeInfo> fetchEpisodesByFilm(String filmId) {
        try {
            var response = filmClient.getEpisodesByFilm(filmId);
            return response != null && response.getResult() != null ? response.getResult() : List.of();
        } catch (FeignException.NotFound exception) {
            throw classifyNotFound(filmId, exception);
        }
    }

    public List<FilmClient.EpisodeInfo> getEpisodesByFilmFallback(String filmId, Throwable throwable) {
        if (throwable instanceof AppException appException) {
            throw appException;
        }
        log.warn("Fallback triggered while fetching episodes for room-service filmId {}: {}",
                filmId,
                throwable.getMessage());
        throw new AppException(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    private AppException classifyNotFound(String filmId, FeignException.NotFound exception) {
        if (FILM_NOT_FOUND_RESPONSE_CODE.matcher(exception.contentUTF8()).find()) {
            return new AppException(ErrorCode.INVALID_FILM);
        }
        log.error("Unexpected 404 while fetching film data for {}; downstream response was not FILM_NOT_FOUND",
                filmId);
        return new AppException(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }
}
