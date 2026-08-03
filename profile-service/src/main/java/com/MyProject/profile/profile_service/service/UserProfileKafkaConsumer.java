package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.document.UserProfileDoc;
import com.MyProject.profile.profile_service.dto.event.ProfileSearchUpdatedEvent;
import com.MyProject.profile.profile_service.dto.event.UserRegisteredEvent;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.repository.elasticsearch.UserProfileElasticRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.MyProject.common.redis.RedisService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileKafkaConsumer {
    UserProfileService userProfileService;
    UserProfileElasticRepository userProfileElasticRepository;
    ObjectMapper objectMapper;
    RedisService redisService;

    private static final String PROCESSED_EVENT_PREFIX = "profile:event:processed:";
    private static final long EVENT_TTL_DAYS = 7;

    @KafkaListener(topics = "user.registered")
    public void listenUserRegistered(String message, Acknowledgment acknowledgment) {
        log.info("Received UserRegisteredEvent: {}", message);
        try {
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            UserProfileCreationRequest request = UserProfileCreationRequest.builder()
                    .userId(event.getUserId())
                    .username(event.getUsername())
                    .email(event.getEmail())
                    .displayName(event.getDisplayName())
                    .firstName(event.getFirstName())
                    .lastName(event.getLastName())
                    .dob(event.getDob())
                    .phoneNumber(event.getPhoneNumber())
                    .city(event.getCity())
                    .joinDate(event.getJoinDate())
                    .build();
            userProfileService.createProfile(request);
            log.info("Successfully created profile for user: {}", event.getUserId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to create profile. Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create profile", e);
        }
    }

    @KafkaListener(topics = "search.sync")
    public void listenSearchSync(String payload, Acknowledgment acknowledgment) {
        log.info("Received profile search sync event: {}", payload);
        try {
            ProfileSearchUpdatedEvent event = objectMapper.readValue(payload, ProfileSearchUpdatedEvent.class);
            
            // Idempotency check
            String processedKey = PROCESSED_EVENT_PREFIX + "search:" + event.getEventId();
            if (redisService.getAsString(processedKey) != null) {
                log.info("Profile search sync already processed for event: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            // Update own Elasticsearch index
            UserProfileDoc doc = UserProfileDoc.builder()
                    .userId(event.getUserId())
                    .username(event.getUsername())
                    .displayName(event.getDisplayName())
                    .avatar(event.getAvatar())
                    .build();
            userProfileElasticRepository.save(doc);

            // Mark as processed
            redisService.setWithExpiration(processedKey, "1", EVENT_TTL_DAYS, TimeUnit.DAYS);

            log.info("Successfully indexed profile to Elasticsearch: {}", event.getUserId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to sync profile to Elasticsearch", e);
            throw new RuntimeException("Failed to sync profile to Elasticsearch", e);
        }
    }
}

