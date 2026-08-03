package com.MyProject.post.post_service.document;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "posts")
public class PostDoc {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String userId;

    @Field(type = FieldType.Text, analyzer = "standard")
    String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    String content;

    @Field(type = FieldType.Keyword)
    String postType;

    @Field(type = FieldType.Date)
    LocalDateTime createdDate;
}
