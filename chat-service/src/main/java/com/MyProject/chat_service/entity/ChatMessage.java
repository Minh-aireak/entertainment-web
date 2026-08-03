package com.MyProject.chat_service.entity;

import com.MyProject.chat_service.enums.MessageStatus;
import com.MyProject.chat_service.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document(collection = "chat-message")
@CompoundIndex(
        name = "conversation_created_idx",
        def = "{'conversationId': 1, 'createdDate': -1}"
)
@CompoundIndex(
        name = "client_msg_sender_conv_idx",
        def = "{'senderId': 1, 'conversationId': 1, 'clientMessageId': 1}",
        unique = true,
        sparse = true
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage {
    @MongoId
    String id;

    String conversationId;

    String senderId;

    MessageType messageType;

    String content;

    long seq;

    String attachmentFileUrl;

    String replyToMessageId;

    String clientMessageId;

    @CreatedDate
    Instant createdDate;

    @LastModifiedDate
    Instant modifiedDate;

    MessageStatus messageStatus;
}
