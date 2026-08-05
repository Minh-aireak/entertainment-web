package com.MyProject.socket_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageResponse {
    String id;
    String conversationId;
    boolean me;
    String messageType;
    String content;
    long seq;
    String attachmentFileUrl;
    String replyToMessageId;
    String replyToSenderId;
    String replyToSenderName;
    String replyToContent;
    String replyToMessageType;
    Instant createdDate;
    Instant modifiedDate;
    String messageStatus;
    String senderId;
    String senderName;
    String senderAvatar;
    String clientMessageId;
}
