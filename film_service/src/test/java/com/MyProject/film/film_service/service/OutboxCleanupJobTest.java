package com.MyProject.film.film_service.service;

import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupJobTest {

    @Mock OutboxRepository outboxRepository;

    OutboxCleanupJob outboxCleanupJob;

    @BeforeEach
    void setUp() {
        outboxCleanupJob = new OutboxCleanupJob(outboxRepository);
    }

    @Test
    void cleanupOldOutboxRecords_happyPath_deletesRecordsOlderThanThreeDays() {
        when(outboxRepository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        outboxCleanupJob.cleanupOldOutboxRecords();

        verify(outboxRepository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupOldOutboxRecords_noOldRecords_deletesZeroWithoutError() {
        when(outboxRepository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(0);

        assertThatCode(() -> outboxCleanupJob.cleanupOldOutboxRecords()).doesNotThrowAnyException();
    }

    @Test
    void cleanupOldOutboxRecords_repositoryThrows_isSwallowedRatherThanPropagated() {
        when(outboxRepository.deleteByCreatedAtBefore(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> outboxCleanupJob.cleanupOldOutboxRecords()).doesNotThrowAnyException();
    }
}
