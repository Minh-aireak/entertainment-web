package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.post.post_service.document.PostDoc;
import com.MyProject.post.post_service.dto.event.EventEnvelope;
import com.MyProject.post.post_service.repository.PostElasticRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class PostSyncKafkaConsumer {
    PostElasticRepository postElasticRepository;
    ObjectMapper objectMapper;
    RedisService redisService;

    private static final String IDEMPOTENCY_KEY_PREFIX = "post:sync:processed:";

    @KafkaListener(topics = "post.sync")
    public void listenPostSync(String payload, Acknowledgment ack) {
        log.info("Received post sync: {}", payload);
        try {
            EventEnvelope<PostDoc> envelope = objectMapper.readValue(payload, new TypeReference<EventEnvelope<PostDoc>>() {});
            String eventId = envelope.getEventId();

            // Check idempotency
            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + eventId;
            if (redisService.getAsString(idempotencyKey) != null) {
                log.info("Post sync event already processed: {}", eventId);
                ack.acknowledge();
                return;
            }

            PostDoc doc = envelope.getPayload();
            postElasticRepository.save(doc);
            log.info("Successfully indexed post to Elasticsearch: {}", doc.getId());

            // Mark as processed
            redisService.setWithExpiration(idempotencyKey, "true", 7, TimeUnit.DAYS);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to sync post to Elasticsearch", e);
            throw new RuntimeException(e);
        }
    }
}
