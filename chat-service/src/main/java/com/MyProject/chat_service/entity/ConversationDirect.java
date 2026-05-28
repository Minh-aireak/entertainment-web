package com.MyProject.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.List;

@Document(collection = "conversation")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@TypeAlias("direct")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationDirect extends Conversation{
    @Indexed(unique = true, sparse = true)
    String participantsHash;

    public static ConversationDirectBuilder<?, ?> fromConversation(Conversation c) {
        return ConversationDirect.builder()
                .id(c.getId())
                .type(c.getType())
                .createdDate(c.getCreatedDate())
                .modifiedDate(c.getModifiedDate());
    }
}
