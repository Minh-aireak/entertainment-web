package com.MyProject.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document("conversation_member")
@CompoundIndex(name = "user_conv_idx", def = "{'userId': 1, 'conversationId': 1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationMember {
    @MongoId
    String id;

    @Indexed
    String conversationId;

    @Indexed
    String userId;

    String lastSeenMessageId;
    Long lastSeenSeq;
    Instant lastSeenAt;
}
