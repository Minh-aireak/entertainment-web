package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.configuration.FilmRateLimitProperties;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilmApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "film:rate-limit:";

    private final RedisService redisService;
    private final FilmRateLimitProperties properties;

    public void checkFilmRating(String userId, String filmId) {
        enforce("film-rating:" + userId + ":" + filmId, properties.getFilmRating());
    }

    public void checkFilmFollow(String userId, String filmId) {
        enforce("film-follow:" + userId + ":" + filmId, properties.getFilmFollow());
    }

    public void checkReadFilms(String userId) {
        enforce("read-films:" + userId, properties.getReadFilms());
    }

    public void checkSearchFilms(String userId) {
        enforce("search-films:" + userId, properties.getSearchFilms());
    }

    public void checkReadFilmDetail(String userId) {
        enforce("read-film-detail:" + userId, properties.getReadFilmDetail());
    }

    public void checkCreateFilm(String userId) {
        enforce("create-film:" + userId, properties.getCreateFilm());
    }

    public void checkReadFollowedFilms(String userId) {
        enforce("read-followed-films:" + userId, properties.getReadFollowedFilms());
    }

    private void enforce(String keySuffix, FilmRateLimitProperties.Rule rule) {
        try {
            long windowSeconds = Math.max(1, rule.getLimitRefreshPeriod().toSeconds());
            boolean allowed = redisService.tryAcquireRateLimit(
                    RATE_LIMIT_PREFIX + keySuffix,
                    rule.getLimitForPeriod(),
                    windowSeconds
            );
            if (!allowed) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Skip custom rate limit check for key {} because Redis is unavailable.", keySuffix, exception);
        }
    }
}
