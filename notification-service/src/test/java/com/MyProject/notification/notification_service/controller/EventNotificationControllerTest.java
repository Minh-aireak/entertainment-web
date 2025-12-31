package com.MyProject.notification.notification_service.controller;

import com.MyProject.common_dto.event.dto.request.EmailRequest;
import com.MyProject.common_dto.event.dto.request.NotificationRequest;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.service.EmailService;
import com.MyProject.notification.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventNotificationControllerTest {

    @InjectMocks
    EventNotificationController eventNotificationController;

    @Mock
    EmailService emailService;

    @Mock
    NotificationService notificationService;

    @Test
    void sendEmail_success() {
        EmailRequest emailRequest = EmailRequest.builder()
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("<h1>Hello</h1>")
                .build();

        eventNotificationController.sendEmail(emailRequest);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(emailService, times(1)).sendEmail(captor.capture());

        SendEmailRequest capturedRequest = captor.getValue();
        assertEquals("Test Subject", capturedRequest.getSubject());
        assertEquals("test@example.com", capturedRequest.getTo().getFirst().getEmail());
    }

    @Test
    void createNotification_success() {
        NotificationRequest request = NotificationRequest.builder()
                .userIdSender("123")
                .build();

        eventNotificationController.createNotification(request);

        verify(notificationService, times(1)).createNotification(any());
    }
}
