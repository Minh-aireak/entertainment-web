package com.MyProject.notification.notification_service.controller;

import com.MyProject.common.dto.request.NotificationRequest;
import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.service.EmailService;
import com.MyProject.notification.notification_service.service.NotificationServiceKafka;
import com.MyProject.notification.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceKafkaTest {

    @InjectMocks
    NotificationServiceKafka notificationServiceKafka;

    @Mock
    EmailService emailService;

    @Mock
    NotificationService notificationService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendEmail_success() throws Exception {
        ReflectionTestUtils.setField(notificationServiceKafka, "objectMapper", objectMapper);

        // JSON matching the EmailRequest structure in notification-service
        String message = "{\"to\":[{\"email\":\"test@example.com\"}],\"subject\":\"Test Subject\",\"htmlContent\":\"<h1>Hello</h1>\"}";

        notificationServiceKafka.sendEmail(message);

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService, times(1)).sendEmail(captor.capture());

        EmailRequest capturedRequest = captor.getValue();
        assertEquals("Test Subject", capturedRequest.getSubject());
        assertEquals("test@example.com", capturedRequest.getTo().getFirst().getEmail());
        assertEquals("<h1>Hello</h1>", capturedRequest.getHtmlContent());
    }

    @Test
    void createNotification_success() {
        NotificationRequest request = NotificationRequest.builder()
                .userIdSender("123")
                .build();

        notificationServiceKafka.createNotification(request);

        verify(notificationService, times(1)).createNotification(any());
    }
}
