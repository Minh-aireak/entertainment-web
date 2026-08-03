package com.MyProject.friend.friend_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.friend.friend_service.document.FriendDoc;
import com.MyProject.friend.friend_service.dto.event.ProfileSearchUpdatedEvent;
import com.MyProject.friend.friend_service.repository.elasticsearch.FriendElasticRepository;
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
public class FriendSyncKafkaConsumer {
    FriendElasticRepository friendElasticRepository;
    ObjectMapper objectMapper;
    RedisService redisService;

    private static final String PROCESSED_EVENT_PREFIX = "friend:event:processed:";
    private static final long EVENT_TTL_DAYS = 7;

    @KafkaListener(topics = "friend.sync")
    public void listenFriendSync(String payload, Acknowledgment ack) {
        log.info("Received friend sync: {}", payload);
        try {
            FriendDoc data = objectMapper.readValue(payload, FriendDoc.class);

            // Idempotency check using FriendDoc.id (since that's the aggregate ID)
            String processedKey = PROCESSED_EVENT_PREFIX + "sync:" + data.getId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Friend sync already processed for id: {}", data.getId());
                ack.acknowledge();
                return;
            }

            friendElasticRepository.save(data);

            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);

            log.info("Successfully synced friend to ES: {}", data.getId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to sync friend to ES", e);
            throw new RuntimeException("Failed to process friend sync event", e);
        }
    }

    @KafkaListener(topics = "search.sync")
    public void listenSearchSync(String payload, Acknowledgment ack) {
        log.info("Received profile search sync in friend service: {}", payload);
        try {
            ProfileSearchUpdatedEvent event = objectMapper.readValue(payload, ProfileSearchUpdatedEvent.class);
            
            // Idempotency check
            String processedKey = PROCESSED_EVENT_PREFIX + "search:" + event.getEventId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Profile search sync already processed for event: {}", event.getEventId());
                ack.acknowledge();
                return;
            }

            // Update FriendDocs in Elasticsearch (implement logic here if needed)
            // For now, log, but you'd want to update all FriendDocs where friendId = event.getUserId()
            log.info("Profile search sync for user: {}, new name: {}, new avatar: {}",
                    event.getUserId(), event.getDisplayName(), event.getAvatar());

            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle profile search sync in friend service", e);
            throw new RuntimeException("Failed to process profile search sync event", e);
        }
    }
}
