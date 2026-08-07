package com.MyProject.socket_service.dto.event;

import com.MyProject.socket_service.dto.response.CommentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreatedEvent {
    // Metadata cho hệ thống
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String producer;

    // Dữ liệu thực tế cho người dùng
    private CommentResponse comment;
}
