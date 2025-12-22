package com.MyProject.notification.notification_service.controller;

import com.MyProject.common_dto.event.dto.FriendRequestEvent;
import com.MyProject.common_dto.event.dto.NotificationEvent;
import com.MyProject.common_dto.event.dto.StatusChangeData;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.entity.TypeNotification;
import com.MyProject.notification.notification_service.service.EmailService;
import com.MyProject.notification.notification_service.service.EventRouterService;
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
    EventRouterService eventRouterService;
    NotificationService notificationService;

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

    @KafkaListener(topics = "friend-request")
    public void handleFriendRequest(FriendRequestEvent event) {
        notificationService.createFriendRequest(event);
    }

    @KafkaListener(topics = "updated-request-events")
    public void handleUpdateFriendRequest(FriendRequestEvent event) {
    }

//    @KafkaListener(topics = "post-created")
//    public void handlePostCreated(Object event) {
//    }

    @KafkaListener(topics = "status-change")
    public void handleStatusChange(StatusChangeData data) {
        notificationService.createNotification(TypeNotification.STATUS_CHANGE, data.fromUserId(), data.listUserIds(), data.message());
    }
}
