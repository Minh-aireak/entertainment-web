package com.MyProject.chat_service.entity;

import com.MyProject.chat_service.enums.ConversationType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
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
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Conversation {
    @MongoId
    String id;

    ConversationType type;

    @CreatedDate
    Instant createdDate;

    @LastModifiedDate
    Instant modifiedDate;

    @Builder.Default
    long totalSeq = 0L;

    @Indexed
    List<String> userIds;

    String lastMessage;

    @Builder.Default
    boolean deleted = false;
}
