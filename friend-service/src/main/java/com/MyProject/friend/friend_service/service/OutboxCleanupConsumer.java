package com.MyProject.friend.friend_service.service;

import com.MyProject.friend.friend_service.repository.mongo.OutboxRepository;
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
                "friend.request.sent",
                "friend.request.accepted.conversation",
                "friend.request.accepted.notification",
                "friend.sync"
        },
        groupId = "friend-outbox-cleanup"
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
