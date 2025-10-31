package com.MyProject.notification.notification_service.controller;

import event.dto.FriendRequestEvent;
import event.dto.NotificationEvent;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.service.EmailService;
import com.MyProject.notification.notification_service.service.EventRouterService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventController {
    EmailService emailService;
    EventRouterService eventRouterService;

    @KafkaListener(topics = "onboard-email")
    public void listenNotificationDelivery(NotificationEvent message){
        emailService.sendEmail(SendEmailRequest.builder()
                        .to(List.of(Recipient.builder()
                                .email(message.getRecipient())
                                .build()))
                        .subject(message.getSubject())
                        .htmlContent(message.getBody())
                .build());
    }

    @KafkaListener(topics = "friend-request-events")
    public void handleFriendRequest(FriendRequestEvent event) {
    }

    @KafkaListener(topics = "updated-request-events")
    public void handleUpdateFriendRequest(FriendRequestEvent event) {
    }

//    @KafkaListener(topics = "post-created")
//    public void handlePostCreated(Object event) {
//    }
}
