package com.MyProject.socket_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationCreatedEvent {
    String id;
    String type;
    String conversationName;
    String conversationAvatar;
    List<String> userIds;
    String lastMessage;
    boolean deleted;
}
