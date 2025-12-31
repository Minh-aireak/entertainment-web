package com.MyProject.common_dto.event.dto.response;

import com.MyProject.common_dto.event.entity.MessageStatus;
import com.MyProject.common_dto.event.entity.MessageType;
import com.MyProject.common_dto.event.entity.ParticipantInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

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
