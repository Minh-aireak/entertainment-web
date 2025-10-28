package com.MyProject.chat_service.dto.request;

import com.MyProject.chat_service.validator.NotBlankChatMessageConstraint;
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

   String messageType;

   @NotBlankChatMessageConstraint(attribute = "Content", message = "ATTRIBUTE_NOT_BLANK")
   @NotNullChatMessageConstraint(attribute = "Content", message = "ATTRIBUTE_NOT_NULL")
   String content;

   String attachmentFileUrl;

   String replyToMessageId;
}
