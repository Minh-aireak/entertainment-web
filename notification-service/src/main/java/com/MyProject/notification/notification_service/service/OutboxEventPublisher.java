package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.entity.Outbox;
import com.MyProject.notification.notification_service.repository.OutboxRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxEventPublisher {
    OutboxRepository outboxRepository;

    public void publish(String aggregateId, String topic, Object payload) {
        outboxRepository.save(Outbox.builder()
                .aggregateId(aggregateId)
                .topic(topic)
                .payload(payload)
                .build());
    }
}
