package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.dto.event.RatingEvent;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FilmRatingServiceKafka {
    FilmRepository filmRepository;
    ObjectMapper objectMapper;
    FilmService filmService;
    RedisService redisService;

    private static final String PROCESSED_EVENT_PREFIX = "film:event:processed:";
    private static final long EVENT_TTL_DAYS = 7;

    @KafkaListener(topics = "film.rating")
    @Transactional
    public void listenRatingEvent(String payload, Acknowledgment ack) {
        log.info("Received rating event for statistics update: {}", payload);
        try {
            RatingEvent event = objectMapper.readValue(payload, RatingEvent.class);
            
            // Idempotency check using eventId
            String processedKey = PROCESSED_EVENT_PREFIX + "rating:" + event.getEventId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Rating event already processed: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            String filmId = event.getFilmId();
            int stars = event.getStars();
            int oldStars = event.getOldStars();

            filmRepository.findById(filmId).ifPresent(film -> {
                double currentTotalStars = film.getAverageRating() * film.getRatingCount();
                double newTotalStars;
                int newCount;

                if (oldStars == 0) {
                    // New rating
                    newTotalStars = currentTotalStars + stars;
                    newCount = film.getRatingCount() + 1;
                } else {
                    // Update existing rating
                    newTotalStars = currentTotalStars - oldStars + stars;
                    newCount = film.getRatingCount();
                }

                film.setRatingCount(newCount);
                film.setAverageRating(newCount > 0 ? newTotalStars / newCount : 0);

                filmRepository.save(film);
                filmService.syncFilmToElasticsearch(film);
                log.info("Updated rating statistics for film {}: averageRating={}, ratingCount={}", 
                        filmId, film.getAverageRating(), film.getRatingCount());
            });

            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process rating statistics update event", e);
            throw new RuntimeException("Failed to process rating event", e);
        }
    }
}
