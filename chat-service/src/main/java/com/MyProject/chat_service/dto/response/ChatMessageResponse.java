package com.MyProject.chat_service.dto.response;

import com.MyProject.chat_service.enums.MessageStatus;
import com.MyProject.chat_service.enums.MessageType;
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
   MessageType messageType;
   String content;
   long seq;
   String attachmentFileUrl;
   String replyToMessageId;
   String replyToSenderId;
   String replyToSenderName;
   String replyToContent;
   MessageType replyToMessageType;
   Instant createdDate;
   Instant modifiedDate;
   MessageStatus messageStatus;
   String senderId;
   String senderName;
   String senderAvatar;
   String clientMessageId;
}
