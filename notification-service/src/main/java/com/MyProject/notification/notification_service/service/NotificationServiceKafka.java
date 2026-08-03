package com.MyProject.notification.notification_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.dto.event.UserRegisteredEvent;
import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceKafka {
    EmailService emailService;
    NotificationService notificationService;
    ObjectMapper objectMapper;
    RedisService redisService;

    private static final String IDEMPOTENCY_KEY_PREFIX = "notification:idempotency:";
    private static final long IDEMPOTENCY_TTL_DAYS = 7;

    @KafkaListener(topics = "user.registered")
    public void handleUserRegistered(ConsumerRecord<String, String> record, Acknowledgment acknowledgment){
        String message = record.value();
        log.info("Received user.registered event: {}", message);
        try {
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);
            
            // Check idempotency
            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + "user-registered:" + event.getEventId();
            if (redisService.getAsString(idempotencyKey) != null) {
                log.info("Event already processed (idempotent): {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            String emailSubject;
            String emailContent;
            
            // Kiểm tra nếu là OAuth2 register (có generatedPassword) hay normal register
            if (event.getGeneratedPassword() != null && !event.getGeneratedPassword().isEmpty()) {
                // OAuth2 register
                emailSubject = "Welcome to AIREAK!";
                emailContent = String.format(
                        "<html>" +
                        "<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"background: linear-gradient(135deg, #00A84E 0%%, #008F41 100%%); padding: 40px; text-align: center; border-radius: 8px 8px 0 0;\">" +
                        "<h1 style=\"color: white; margin: 0; font-size: 28px;\">Welcome to AIREAK, %s!</h1>" +
                        "</div>" +
                        "<div style=\"padding: 40px; background-color: #f9f9f9; border-radius: 0 0 8px 8px;\">" +
                        "<p style=\"font-size: 16px; margin-bottom: 20px;\">Hi %s,</p>" +
                        "<p style=\"font-size: 16px; margin-bottom: 20px;\">You have successfully registered as a member of AIREAK!</p>" +
                        "<div style=\"background-color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; border: 1px solid #e0e0e0;\">" +
                        "<h3 style=\"color: #00A84E; margin-top: 0;\">Your Account Credentials</h3>" +
                        "<p style=\"margin: 10px 0;\"><strong>Username:</strong> %s</p>" +
                        "<p style=\"margin: 10px 0;\"><strong>Password:</strong> %s</p>" +
                        "</div>" +
                        "<p style=\"font-size: 16px; margin-bottom: 20px;\">Please remember to change your password as soon as possible for your account security.</p>" +
                        "<p style=\"font-size: 16px; margin-bottom: 30px;\">You can reset your password immediately by clicking the link below:</p>" +
                        "<div style=\"text-align: center;\">" +
                        "<a href=\"%s\" style=\"background-color: #00A84E; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;\">Reset Password</a>" +
                        "</div>" +
                        "<p style=\"margin-top: 30px; font-size: 14px; color: #888;\">Best regards,<br>The AIREAK Team</p>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                        event.getDisplayName(),
                        event.getFirstName() != null ? event.getFirstName() : event.getUsername(),
                        event.getUsername(),
                        event.getGeneratedPassword(),
                        event.getResetPasswordUrl()
                );
            } else {
                // Normal register
                emailSubject = "Welcome to AIREAK!";
                emailContent = String.format(
                        "<html>" +
                        "<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto;\">" +
                        "<div style=\"background: linear-gradient(135deg, #00A84E 0%%, #008F41 100%%); padding: 40px; text-align: center; border-radius: 8px 8px 0 0;\">" +
                        "<h1 style=\"color: white; margin: 0; font-size: 28px;\">Welcome to AIREAK, %s!</h1>" +
                        "</div>" +
                        "<div style=\"padding: 40px; background-color: #f9f9f9; border-radius: 0 0 8px 8px;\">" +
                        "<p style=\"font-size: 16px; margin-bottom: 20px;\">Hi %s,</p>" +
                        "<p style=\"font-size: 16px; margin-bottom: 20px;\">Thank you for signing up for AIREAK! We're excited to have you on board.</p>" +
                        "<p style=\"font-size: 16px; margin-bottom: 30px;\">Start exploring amazing entertainment content today!</p>" +
                        "<div style=\"text-align: center;\">" +
                        "<a href=\"http://localhost:5173\" style=\"background-color: #00A84E; color: white; padding: 12px 30px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;\">Get Started</a>" +
                        "</div>" +
                        "<p style=\"margin-top: 30px; font-size: 14px; color: #888;\">Best regards,<br>The AIREAK Team</p>" +
                        "</div>" +
                        "</body>" +
                        "</html>",
                        event.getDisplayName(),
                        event.getFirstName() != null ? event.getFirstName() : event.getUsername()
                );
            }
            
            // Convert to EmailRequest and send
            EmailRequest emailRequest = EmailRequest.builder()
                    .to(List.of(Recipient.builder().email(event.getEmail()).build()))
                    .subject(emailSubject)
                    .htmlContent(emailContent)
                    .build();
            
            emailService.sendEmail(emailRequest);
            
            // Mark event as processed
            redisService.setWithExpiration(idempotencyKey, "processed", IDEMPOTENCY_TTL_DAYS, TimeUnit.DAYS);
            
            log.info("Successfully sent welcome email to: {}", event.getEmail());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user.registered event", e);
            throw new RuntimeException("Failed to process user.registered event", e);
        }
    }

    @KafkaListener(topics = "notification.events")
    public void createNotification(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String message = record.value();
        try {
            NotificationEvent request = objectMapper.readValue(message, NotificationEvent.class);
            
            // Check idempotency
            String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + "notification:" + request.getEventId();
            if (redisService.getAsString(idempotencyKey) != null) {
                log.info("Event already processed (idempotent): {}", request.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            log.info("Processing notification event");
            notificationService.createNotification(request);
            
            // Mark event as processed
            redisService.setWithExpiration(idempotencyKey, "processed", IDEMPOTENCY_TTL_DAYS, TimeUnit.DAYS);
            
            log.info("Successfully processed notification event");
            acknowledgment.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize notification event message: {}", message, e);
            throw new RuntimeException("Failed to deserialize notification event message", e);
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
            throw new RuntimeException("Failed to process notification event", e);
        }
    }
}
