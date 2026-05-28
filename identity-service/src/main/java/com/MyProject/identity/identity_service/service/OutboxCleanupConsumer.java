package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.repository.OutboxRepository;
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
        topics = {
            "user.created", 
            "email.sent"
        }, 
        groupId = "identity-outbox-cleanup"
    )
    public void cleanup(ConsumerRecord<String, String> record) {
        String outboxId = record.key();
        
        if (outboxId == null) {
            log.warn("Received message with null key on topic {}", record.topic());
            return;
        }

        try {
            log.info("Successfully published event detected via Debezium. Deleting outbox record: {}", outboxId);
            outboxRepository.deleteById(outboxId);
        } catch (Exception e) {
            log.error("Failed to delete outbox record: {}", outboxId, e);
        }
    }
}
