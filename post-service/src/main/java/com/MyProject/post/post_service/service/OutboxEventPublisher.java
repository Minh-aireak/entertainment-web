package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.dto.event.EventEnvelope;
import com.MyProject.post.post_service.entity.Outbox;
import com.MyProject.post.post_service.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventPublisher {
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    public <T> void publish(String topic, String aggregateId, T payload) {
        try {
            String eventId = UUID.randomUUID().toString();
            EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                    .eventId(eventId)
                    .aggregateId(aggregateId)
                    .payload(payload)
                    .build();
            String envelopeJson = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(Outbox.builder()
                    .id(UUID.randomUUID().toString())
                    .eventId(eventId)
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(envelopeJson)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload for outbox", e);
            throw new RuntimeException("Failed to serialize payload for outbox", e);
        }
    }
}
