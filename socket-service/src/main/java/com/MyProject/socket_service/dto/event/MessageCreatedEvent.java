package com.MyProject.socket_service.dto.event;

import com.MyProject.socket_service.dto.response.ChatMessageResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class MessageCreatedEvent {
    // Metadata cho hệ thống
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String producer;
    private List<String> receiverIds;

    // Dữ liệu thực tế cho người dùng
    private ChatMessageResponse message;
}
