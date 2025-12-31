package com.MyProject.socket_service.controller;

import com.MyProject.common_dto.event.dto.NotificationSocketData;
import com.MyProject.socket_service.service.WebSocketSessionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventSocketController {
    WebSocketSessionService webSocketSessionService;

//    @KafkaListener(topics = "send-message")
//    public void handleSendMessageEvent(ChatMessageResponse response) {
//        webSocketSessionService.sendToRoom(response.getConversationId(), "message", response);
//    }

    @KafkaListener(topics = "notification")
    public void handleNotificationEventToUser(NotificationSocketData notificationSocketData) {
        webSocketSessionService.publishToUsers(notificationSocketData.userIds(), "notification", notificationSocketData.notificationResponse());
    }
}
