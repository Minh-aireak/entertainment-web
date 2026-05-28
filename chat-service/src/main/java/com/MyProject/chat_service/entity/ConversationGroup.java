package com.MyProject.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "conversation")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@TypeAlias("group")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationGroup extends Conversation{
    String groupName;
    String groupOwner;
    String groupAvatar;

    public static ConversationGroup.ConversationGroupBuilder<?, ?> fromConversation(Conversation c) {
        return ConversationGroup.builder()
                .id(c.getId())
                .type(c.getType())
                .createdDate(c.getCreatedDate())
                .modifiedDate(c.getModifiedDate());
    }
}
