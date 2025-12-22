package com.MyProject.notification.notification_service.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EventRouterService {
    KafkaTemplate<String, Object> kafkaTemplate;

//    public void routeProfileUpdatedEvent(ProfileUpdatedEvent event) {
//        kafkaTemplate.send("post-profile-updated", event);
//
//        kafkaTemplate.send("chat-profile-updated", event);
//    }

//    public void routeNotificationEvent(String topic, Object event) {
//        try {
//            kafkaTemplate.send(topic, event);
//            log.info("Successfully routed event to topic: {}", topic);
//        } catch (Exception e) {
//            log.error("Failed to route event to topic {}: {}", topic, e.getMessage());
//        }
//    }
}