package com.MyProject.chat_service.document;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "conversations")
public class ConversationDoc {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String type;

    @Field(type = FieldType.Text, analyzer = "standard")
    String conversationName;

    @Field(type = FieldType.Keyword)
    String conversationAvatar;

    @Field(type = FieldType.Keyword)
    List<String> userIds;

    @Field(type = FieldType.Long)
    Long totalSeq;

    @Field(type = FieldType.Date)
    Instant createdDate;

    @Field(type = FieldType.Date)
    Instant modifiedDate;

    @Field(type = FieldType.Keyword)
    String participantsHash;

    @Field(type = FieldType.Keyword)
    String groupOwner;

    @Field(type = FieldType.Text)
    String lastMessage;

    @Builder.Default
    @Field(type = FieldType.Boolean)
    boolean deleted = false;
}
