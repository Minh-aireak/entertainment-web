package com.MyProject.post.post_service.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "post_like")
@CompoundIndex(name = "post_user_unique", def = "{'postId': 1, 'userId': 1}", unique = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostLike {
    @MongoId
    String id;

    @Indexed
    String postId;

    @Indexed
    String userId;

    LocalDateTime createdDate;
}
