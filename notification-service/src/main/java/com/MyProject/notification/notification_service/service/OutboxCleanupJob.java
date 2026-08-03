package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanupJob {
    private final OutboxRepository outboxRepository;

    // Chạy mỗi 3 ngày vào lúc 2:00 sáng
    @Scheduled(cron = "0 0 2 */3 * *")
    @Transactional
    public void cleanupOldOutboxRecords() {
        log.info("Starting outbox cleanup job...");
        
        try {
            // Xóa các bản ghi cũ hơn 3 ngày
            Instant threeDaysAgo = Instant.now().minusSeconds(3 * 24 * 60 * 60);
            outboxRepository.deleteByCreatedDateBefore(threeDaysAgo);
            
            log.info("Successfully cleaned up old outbox records");
        } catch (Exception e) {
            log.error("Failed to cleanup outbox records", e);
        }
    }
}
