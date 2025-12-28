package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {
    @Autowired
    TokenCleanupService tokenCleanupService;

    @MockitoBean
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
