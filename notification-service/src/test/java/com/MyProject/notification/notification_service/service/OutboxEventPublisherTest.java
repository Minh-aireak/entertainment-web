package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.event.NotificationSocket;
import com.MyProject.notification.notification_service.entity.Outbox;
import com.MyProject.notification.notification_service.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {
    @Test
    void publish_serializesPayloadAsJsonString() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxEventPublisher publisher = new OutboxEventPublisher(repository, objectMapper);
        NotificationSocket payload = new NotificationSocket(
                "Sender",
                "avatar.png",
                "FRIEND_REQUEST",
                "Friend request",
                "Sender sent you a friend request",
                List.of("receiver")
        );

        publisher.publish("notification-id", "notification", payload);

        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(repository).save(captor.capture());
        Outbox savedOutbox = captor.getValue();
        assertEquals("notification", savedOutbox.getTopic());
        assertEquals("Sender", objectMapper.readTree(savedOutbox.getPayload()).get("displayNameSender").asText());
        assertEquals("FRIEND_REQUEST", objectMapper.readTree(savedOutbox.getPayload()).get("type").asText());
        assertEquals("receiver", objectMapper.readTree(savedOutbox.getPayload()).get("toUserIds").get(0).asText());
    }
}
