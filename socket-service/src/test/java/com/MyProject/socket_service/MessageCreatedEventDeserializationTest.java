package com.MyProject.socket_service;

import com.MyProject.socket_service.dto.event.MessageCreatedEvent;
import com.MyProject.socket_service.dto.response.ChatMessageResponse;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageCreatedEventDeserializationTest {
    @Test
    void kafkaMessagePayloadCanBeDeserialized() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        MessageCreatedEvent source = MessageCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .receiverIds(List.of(UUID.randomUUID().toString()))
                .message(ChatMessageResponse.builder()
                        .id(UUID.randomUUID().toString())
                        .conversationId(conversationId)
                        .createdDate(Instant.now())
                        .build())
                .build();
        var objectMapper = JsonMapper.builder().findAndAddModules().build();

        String payload = objectMapper.writeValueAsString(source);
        MessageCreatedEvent result = objectMapper.readValue(payload, MessageCreatedEvent.class);

        assertEquals(conversationId, result.getMessage().getConversationId());
    }
}
