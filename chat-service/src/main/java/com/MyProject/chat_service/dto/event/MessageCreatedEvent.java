package com.MyProject.chat_service.dto.event;

import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class MessageCreatedEvent {
    // Metadata
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String producer;
    private List<String> receiverIds;

    // Dữ liệu thực tế cho người dùng
    private ChatMessageResponse message;
}
