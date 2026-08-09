package com.MyProject.socket_service.service;

import com.MyProject.socket_service.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

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
        outboxCleanupJob.cleanupOldOutboxRecords();

        verify(outboxRepository).deleteByCreatedAtBefore(any(Instant.class));
    }

    @Test
    void cleanupOldOutboxRecords_repositoryThrows_isSwallowedRatherThanPropagated() {
        doThrow(new RuntimeException("mongo down")).when(outboxRepository).deleteByCreatedAtBefore(any(Instant.class));

        assertThatCode(() -> outboxCleanupJob.cleanupOldOutboxRecords()).doesNotThrowAnyException();
    }
}
