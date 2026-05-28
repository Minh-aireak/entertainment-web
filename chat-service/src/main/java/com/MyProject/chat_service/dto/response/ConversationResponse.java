package com.MyProject.chat_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationResponse {
   String id;
   String type;
   List<String> userIds;
   Long totalSeq;
   Instant createdDate;
   Instant modifiedDate;

   // Direct chat
   String participantsHash;
   String directName;
   String directAvatar;

   // Group chat
   String groupName;
   String groupOwner;
   String groupAvatar;

   String lastMessage;
   boolean deleted;

   List<ParticipantResponse> participants;
}
