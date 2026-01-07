package com.MyProject.notification.notification_service.controller;

import com.MyProject.common.dto.request.EmailRequest;
import com.MyProject.common.dto.request.NotificationRequest;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.service.EmailService;
import com.MyProject.notification.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventNotificationController {
    EmailService emailService;
    NotificationService notificationService;

    @KafkaListener(topics = "send-email")
    public void sendEmail(EmailRequest message){
        emailService.sendEmail(SendEmailRequest.builder()
                        .to(List.of(Recipient.builder()
                                .email(message.getRecipient())
                                .build()))
                        .subject(message.getSubject())
                        .htmlContent(message.getBody())
                .build());
    }

    @KafkaListener(topics = "create-notification")
    public void createNotification(NotificationRequest request) {
        notificationService.createNotification(request);
    }


}
