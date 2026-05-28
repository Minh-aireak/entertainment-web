package com.MyProject.friend.friend_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document(collection = "user_relationship")
@CompoundIndex(
        name = "idx_friend_query",
        def = "{'senderId':1,'receiverId':1,'relationshipStatus':1}"
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRelationship {
    @MongoId
    String id;

    String senderId;

    String receiverId;

    @Indexed(unique = true)
    String hashFriend;

    RelationshipStatus relationshipStatus;

    @CreatedDate
    Instant acceptAt;
}
