package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.entity.Outbox;
import com.MyProject.identity.identity_service.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventPublisher {
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    public void publish(String aggregateId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Error serializing outbox event", e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}
