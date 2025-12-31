package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.repository.httpclient.EmailClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    EmailClient emailClient;

    @InjectMocks
    EmailService emailService;

    final String mockApiKey = "test-api-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "apiKey", mockApiKey);
    }

    @Test
    void sendEmail_success() {
        SendEmailRequest request = SendEmailRequest.builder()
                .to(List.of(Recipient.builder().email("user@gmail.com").build()))
                .subject("Welcome")
                .htmlContent("<p>Hello</p>")
                .build();

        emailService.sendEmail(request);

        verify(emailClient, times(1)).sendEmail(eq(mockApiKey), any());
    }

    @Test
    void sendEmail_failure_throwsAppException() {
        SendEmailRequest request = SendEmailRequest.builder()
                .to(List.of(Recipient.builder().email("error@gmail.com").build()))
                .build();

        when(emailClient.sendEmail(anyString(), any()))
                .thenThrow(mock(FeignException.class));

        AppException exception = assertThrows(AppException.class, () -> {
            emailService.sendEmail(request);
        });

        assertEquals(ErrorCode.CANNOT_SEND_EMAIL, exception.getErrorCode());
        verify(emailClient, times(1)).sendEmail(anyString(), any());
    }
}