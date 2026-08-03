package com.MyProject.film.film_service.service;

import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
            LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
            int deletedCount = outboxRepository.deleteByCreatedAtBefore(threeDaysAgo);
            
            log.info("Successfully deleted {} old outbox records", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup outbox records", e);
        }
    }
}
