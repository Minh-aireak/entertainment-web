package com.MyProject.chat_service.document;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "messages")
public class ChatMessageDoc {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String conversationId;

    @Field(type = FieldType.Keyword)
    String senderId;

    @Field(type = FieldType.Text, analyzer = "standard")
    String content;

    @Field(type = FieldType.Date)
    Instant createdAt;

    @Field(type = FieldType.Long)
    long seq;

    @Field(type = FieldType.Keyword)
    String clientMessageId;

    @Builder.Default
    @Field(type = FieldType.Boolean)
    boolean deleted = false;
}
