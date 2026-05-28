package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.document.UserProfileDoc;
import com.MyProject.profile.profile_service.dto.event.UserCreatedEvent;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.repository.elasticsearch.UserProfileElasticRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileKafkaConsumer {
    UserProfileService userProfileService;
    UserProfileElasticRepository userProfileElasticRepository;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "user.created")
    public void listenUserCreated(String message) {
        log.info("Received UserCreatedEvent: {}", message);
        try {
            UserCreatedEvent event = objectMapper.readValue(message, UserCreatedEvent.class);
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
        } catch (Exception e) {
            log.error("Failed to create profile. Error: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "profile.sync")
    public void listenProfileSync(String payload) {
        log.info("Received profile sync event: {}", payload);
        try {
            UserProfileDoc doc = objectMapper.readValue(payload, UserProfileDoc.class);
            userProfileElasticRepository.save(doc);
            log.info("Successfully indexed profile to Elasticsearch: {}", doc.getUserId());
        } catch (Exception e) {
            log.error("Failed to sync profile to Elasticsearch", e);
        }
    }
}
