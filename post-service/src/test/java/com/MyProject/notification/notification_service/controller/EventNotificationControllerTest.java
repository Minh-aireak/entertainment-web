package com.MyProject.notification.notification_service.controller;

import com.MyProject.common.dto.request.EmailRequest;
import com.MyProject.common.dto.request.NotificationRequest;
import com.MyProject.post.post_service.controller.EvenPostController;
import com.MyProject.post.post_service.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventNotificationControllerTest {

    @InjectMocks
    EvenPostController evenPostController;

    @Mock
    PostService postService;

    @Test
    void handleProfileUpdatedEvent_success() {
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
