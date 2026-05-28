package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.repository.mongo.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanupConsumer {
    private final OutboxRepository outboxRepository;

    @KafkaListener(
        topics = "profile.sync",
        groupId = "profile-outbox-cleanup"
    )
    public void cleanup(ConsumerRecord<String, Object> record) {
        String outboxId = record.key() != null ? record.key() : null;
        
        if (outboxId == null) {
            log.warn("Received message with null key on topic {}", record.topic());
            return;
        }

        try {
            log.info("Successfully published event detected via Debezium MongoDB. Deleting outbox record: {}", outboxId);
            outboxRepository.deleteById(outboxId);
        } catch (Exception e) {
            log.error("Failed to delete outbox record: {}", outboxId, e);
        }
    }
}
