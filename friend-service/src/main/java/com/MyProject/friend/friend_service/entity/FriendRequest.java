package com.MyProject.friend.friend_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document(collection = "friend_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequest {
    @MongoId
    String id;

    String senderId;

    String receiverId;

    @Indexed(unique = true)
    String hashFriendRequest;

    FriendRequestStatus friendRequestStatus;

    @CreatedDate
    Instant createdAt;
}
