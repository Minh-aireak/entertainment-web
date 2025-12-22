package com.MyProject.chat_service.dto.response;

import com.MyProject.chat_service.entity.ParticipantInfo;
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
   String participantsHash;
   List<ParticipantInfo> participantInfos;
   String directName;
   String directAvatar;
   String groupName;
   String groupOwner;
   String groupAvatar;
   Instant createdDate;
   Instant modifiedDate;
}
