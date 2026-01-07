package com.MyProject.chat_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageDeleteRequest {
   String chatMessageId;
   String conversationId;
   String deleteType;
}
