package com.MyProject.chat_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSeenEvent {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String conversationId;
    private String userId;
    private String lastSeenMessageId;
    private List<String> receiverIds;
}
