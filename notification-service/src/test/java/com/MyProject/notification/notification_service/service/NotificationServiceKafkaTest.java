package com.MyProject.notification.notification_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceKafkaTest {

    @Mock EmailService emailService;
    @Mock NotificationService notificationService;
    @Mock RedisService redisService;
    @Mock Acknowledgment ack;

    NotificationServiceKafka notificationServiceKafka;

    @BeforeEach
    void setUp() {
        notificationServiceKafka = new NotificationServiceKafka(
                emailService, notificationService, new ObjectMapper().registerModule(new JavaTimeModule()), redisService);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("topic", 0, 0, "key", value);
    }

    // ---------- handleUserRegistered ----------

    @Test
    void handleUserRegistered_normalRegister_sendsWelcomeEmailAndMarksProcessed() {
        when(redisService.getAsString("notification:idempotency:user-registered:e-1")).thenReturn(null);
        String payload = "{\"eventId\":\"e-1\",\"email\":\"user@test.com\",\"username\":\"user1\",\"displayName\":\"User One\"}";

        notificationServiceKafka.handleUserRegistered(record(payload), ack);

        verify(emailService).sendEmail(argThat((EmailRequest req) ->
                req.getTo().get(0).getEmail().equals("user@test.com") && req.getSubject().equals("Welcome to AIREAK!")));
        verify(redisService).setWithExpiration(eq("notification:idempotency:user-registered:e-1"), eq("processed"), eq(7L), any());
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistered_oauth2Register_sendsEmailWithGeneratedPassword() {
        when(redisService.getAsString(anyString())).thenReturn(null);
        String payload = "{\"eventId\":\"e-1\",\"email\":\"user@test.com\",\"username\":\"user1\",\"displayName\":\"User One\","
                + "\"generatedPassword\":\"Temp123!\",\"resetPasswordUrl\":\"https://app/reset\"}";

        notificationServiceKafka.handleUserRegistered(record(payload), ack);

        verify(emailService).sendEmail(argThat((EmailRequest req) -> req.getHtmlContent().contains("Temp123!")));
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistered_alreadyProcessed_skipsEmailButStillAcks() {
        when(redisService.getAsString("notification:idempotency:user-registered:e-1")).thenReturn("processed");
        String payload = "{\"eventId\":\"e-1\",\"email\":\"user@test.com\"}";

        notificationServiceKafka.handleUserRegistered(record(payload), ack);

        verifyNoInteractions(emailService);
        verify(ack).acknowledge();
    }

    @Test
    void handleUserRegistered_malformedPayload_throwsAndDoesNotAck() {
        assertThatThrownBy(() -> notificationServiceKafka.handleUserRegistered(record("not-json"), ack))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(emailService, ack);
    }

    @Test
    void handleUserRegistered_emailServiceThrows_propagatesAsRuntimeExceptionAndDoesNotAck() {
        when(redisService.getAsString(anyString())).thenReturn(null);
        doThrow(new RuntimeException("email provider down")).when(emailService).sendEmail(any());
        String payload = "{\"eventId\":\"e-1\",\"email\":\"user@test.com\",\"displayName\":\"User One\"}";

        assertThatThrownBy(() -> notificationServiceKafka.handleUserRegistered(record(payload), ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    // ---------- createNotification ----------

    @Test
    void createNotification_happyPath_delegatesToNotificationServiceAndMarksProcessed() {
        when(redisService.getAsString("notification:idempotency:notification:e-2")).thenReturn(null);
        String payload = "{\"eventId\":\"e-2\",\"typeNotification\":\"FILM_LIKE\",\"userIdSender\":\"user-1\",\"toUserIds\":[\"user-2\"]}";

        notificationServiceKafka.createNotification(record(payload), ack);

        verify(notificationService).createNotification(argThat((NotificationEvent e) -> e.getEventId().equals("e-2")));
        verify(redisService).setWithExpiration(eq("notification:idempotency:notification:e-2"), eq("processed"), eq(7L), any());
        verify(ack).acknowledge();
    }

    @Test
    void createNotification_alreadyProcessed_skipsButStillAcks() {
        when(redisService.getAsString("notification:idempotency:notification:e-2")).thenReturn("processed");
        String payload = "{\"eventId\":\"e-2\",\"typeNotification\":\"FILM_LIKE\"}";

        notificationServiceKafka.createNotification(record(payload), ack);

        verifyNoInteractions(notificationService);
        verify(ack).acknowledge();
    }

    @Test
    void createNotification_malformedPayload_throwsAndDoesNotAck() {
        assertThatThrownBy(() -> notificationServiceKafka.createNotification(record("not-json"), ack))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(notificationService, ack);
    }

    @Test
    void createNotification_serviceThrows_propagatesAndDoesNotAck() {
        when(redisService.getAsString(anyString())).thenReturn(null);
        doThrow(new RuntimeException("db down")).when(notificationService).createNotification(any());
        String payload = "{\"eventId\":\"e-2\",\"typeNotification\":\"FILM_LIKE\",\"toUserIds\":[\"user-2\"]}";

        assertThatThrownBy(() -> notificationServiceKafka.createNotification(record(payload), ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    // ---------- consumeConversationSeen ----------

    @Test
    void consumeConversationSeen_happyPath_marksChatConversationReadAndAcks() {
        String payload = "{\"userId\":\"user-1\",\"conversationId\":\"conv-1\"}";

        notificationServiceKafka.consumeConversationSeen(payload, ack);

        verify(notificationService).markChatConversationRead("user-1", "conv-1");
        verify(ack).acknowledge();
    }

    @Test
    void consumeConversationSeen_malformedPayload_throwsAndDoesNotAck() {
        assertThatThrownBy(() -> notificationServiceKafka.consumeConversationSeen("not-json", ack))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(notificationService, ack);
    }

    @Test
    void consumeConversationSeen_serviceThrows_propagatesAndDoesNotAck() {
        doThrow(new RuntimeException("db down")).when(notificationService).markChatConversationRead(any(), any());
        String payload = "{\"userId\":\"user-1\",\"conversationId\":\"conv-1\"}";

        assertThatThrownBy(() -> notificationServiceKafka.consumeConversationSeen(payload, ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }
}
