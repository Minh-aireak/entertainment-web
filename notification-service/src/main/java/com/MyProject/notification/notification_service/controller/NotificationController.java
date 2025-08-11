package com.MyProject.notification.notification_service.controller;

import com.MyProject.event.dto.NotificationEvent;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.service.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    EmailService emailService;

    @KafkaListener(topics = "notification-delivery")
    public void listenNotificationDelivery(NotificationEvent message){
        emailService.sendEmail(SendEmailRequest.builder()
                        .to(List.of(Recipient.builder()
                                .email(message.getRecipient())
                                .build()))
                        .subject(message.getSubject())
                        .htmlContent(message.getBody())
                .build());
    }
}
