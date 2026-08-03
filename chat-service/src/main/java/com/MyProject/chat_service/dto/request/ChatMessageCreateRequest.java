package com.MyProject.chat_service.dto.request;

import com.MyProject.chat_service.enums.MessageType;
import com.MyProject.chat_service.validator.NotNullChatMessageConstraint;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageCreateRequest {
   @NotNullChatMessageConstraint(attribute = "Conversation Id", message = "ATTRIBUTE_NOT_NULL")
   String conversationId;

   @NotNullChatMessageConstraint(attribute = "Message Type", message = "ATTRIBUTE_NOT_NULL")
   MessageType messageType;

   String content;

   String attachmentFileUrl;

   String replyToMessageId;

   String clientMessageId;
}
