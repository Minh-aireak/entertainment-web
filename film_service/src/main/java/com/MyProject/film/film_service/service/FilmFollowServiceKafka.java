package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.dto.event.FollowEvent;
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
public class FilmFollowServiceKafka {
    FilmRepository filmRepository;
    ObjectMapper objectMapper;
    FilmService filmService;
    RedisService redisService;

    private static final String PROCESSED_EVENT_PREFIX = "film:event:processed:";
    private static final long EVENT_TTL_DAYS = 7;

    @KafkaListener(topics = "film.follow")
    @Transactional
    public void listenFollowEvent(String payload, Acknowledgment ack) {
        log.info("Received follow event for count update: {}", payload);
        try {
            FollowEvent event = objectMapper.readValue(payload, FollowEvent.class);
            
            // Idempotency check using eventId
            String processedKey = PROCESSED_EVENT_PREFIX + "follow:" + event.getEventId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Follow event already processed: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            String filmId = event.getFilmId();
            String action = event.getAction();

            if ("FOLLOW".equals(action)) {
                filmRepository.findById(filmId).ifPresent(film -> {
                    film.setFollowCount(film.getFollowCount() + 1);
                    filmRepository.save(film);
                    filmService.syncFilmToElasticsearch(film);
                    log.info("Incremented follow count for film {}", filmId);
                });
            } else if ("UNFOLLOW".equals(action)) {
                filmRepository.findById(filmId).ifPresent(film -> {
                    film.setFollowCount(Math.max(0, film.getFollowCount() - 1));
                    filmRepository.save(film);
                    filmService.syncFilmToElasticsearch(film);
                    log.info("Decremented follow count for film {}", filmId);
                });
            }

            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process follow count update event", e);
            throw new RuntimeException("Failed to process follow event", e);
        }
    }
}
