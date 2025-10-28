package com.MyProject.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.Map;

@Document(collection = "chat-message")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage {
    @MongoId
    String id;

    @Indexed
    String conversationId;

    @Indexed
    ParticipantInfo sender;

    MessageType messageType;

    String content;

    String attachmentFileUrl;

    String replyToMessageId;

    Instant createdDate;
    Instant modifiedDate;

    MessageStatus messageStatus;
    Map<String, Instant> seenAtMap;
}
