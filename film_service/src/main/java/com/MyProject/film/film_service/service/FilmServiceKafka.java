package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.document.FilmDoc;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FilmServiceKafka {
    FilmElasticRepository filmElasticRepository;
    ObjectMapper objectMapper;
    RedisService redisService;

    private static final String PROCESSED_EVENT_PREFIX = "film:event:processed:";
    private static final long EVENT_TTL_DAYS = 7;

    @KafkaListener(topics = "film.sync")
    public void listenFilmSync(String payload, Acknowledgment ack) {
        log.info("Received film sync: {}", payload);
        try {
            FilmDoc doc = objectMapper.readValue(payload, FilmDoc.class);
            
            // Idempotency check using film ID as key (since film sync is idempotent by film ID)
            String processedKey = PROCESSED_EVENT_PREFIX + "sync:" + doc.getId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Film sync already processed for film: {}", doc.getId());
                ack.acknowledge();
                return;
            }

            filmElasticRepository.save(doc);
            
            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);
            
            log.info("Successfully indexed film to Elasticsearch: {}", doc.getId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to sync film to Elasticsearch", e);
            throw new RuntimeException("Failed to process film sync event", e);
        }
    }
}
