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
public class ChatMessageDeleteRequest {
   @NotNullChatMessageConstraint(attribute = "Message Id", message = "ATTRIBUTE_NOT_BLANK")
   String chatMessageId;

   @NotNullChatMessageConstraint(attribute = "Delete Type", message = "ATTRIBUTE_NOT_NULL")
   @NotBlankChatMessageConstraint(attribute = "Delete Type", message = "ATTRIBUTE_NOT_BLANK")
   String deleteType;
}
