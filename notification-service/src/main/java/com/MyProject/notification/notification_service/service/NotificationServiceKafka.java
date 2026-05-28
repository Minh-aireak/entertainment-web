package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceKafka {
    EmailService emailService;
    NotificationService notificationService;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "email.sent")
    public void sendEmail(String message){
        log.info("Received email.sent event: {}", message);
        try {
            EmailRequest emailRequest = objectMapper.readValue(message, EmailRequest.class);
            emailService.sendEmail(emailRequest);
        } catch (Exception e) {
            log.error("Failed to process email.sent event", e);
        }
    }

    @KafkaListener(topics = {
            "friend.request.sent",
            "friend.request.accepted.notification",
            "chat.message.created.notification"
    })
    public void createNotification(ConsumerRecord<String, String> record) {
        String topic = record.topic();
        String message = record.value();

        try {
            NotificationEvent request = objectMapper.readValue(message, NotificationEvent.class);
            log.info("Processing notification event from topic: [{}]", topic);
            notificationService.createNotification(request);
            log.info("Successfully processed notification event from topic: [{}]", topic);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize message from topic: [{}], message: {}", topic, message, e);
        } catch (Exception e) {
            log.error("Failed to process notification event from topic: [{}]", topic, e);
        }
    }
}
