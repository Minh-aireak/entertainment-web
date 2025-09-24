package com.MyProject.chat_service.dto.request;

import com.MyProject.chat_service.entity.ParticipantInfo;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationRequest {
   String type;

   @NotNull
   List<ParticipantInfo> participantInfos;
}
