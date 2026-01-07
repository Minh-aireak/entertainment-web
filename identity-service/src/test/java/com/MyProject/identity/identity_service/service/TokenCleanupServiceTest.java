package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {
    @InjectMocks
    TokenCleanupService tokenCleanupService;

    @Mock
    InvalidatedTokenRepository invalidatedTokenRepository;

    @Captor
    ArgumentCaptor<Date> dateCaptor;

    @Test
    void cleanupExpiredTime_success() {
        when(invalidatedTokenRepository.deleteAllByExpiryTimeBefore(any(Date.class))).thenReturn(7);

        tokenCleanupService.cleanupExpiredTime();

        verify(invalidatedTokenRepository, times(1)).deleteAllByExpiryTimeBefore(dateCaptor.capture());

        Date passedDate = dateCaptor.getValue();
        assertNotNull(passedDate);
        assertTrue(passedDate.getTime() <= new Date().getTime());
    }
}
