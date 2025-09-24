package com.MyProject.post.post_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "post")
@TypeAlias("business-schedule")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Post {
    @MongoId
    String id;
    PostType postType;

    String userId;
    String displayName;
    String title;
    String content;
    LocalDateTime startTime;
    LocalDateTime endTime;
    LocalDateTime createdDate;
    String status;
}
