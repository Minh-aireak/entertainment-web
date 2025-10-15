package com.MyProject.friend_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "friend_requests",
        indexes = {
                @Index(name = "idx_hash_friend_request", columnList = "hash_friend_request")
        })
public class FriendRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "user_id", nullable = false)
    String userId;

    @Column(name = "to_user_id", nullable = false)
    String toUserId;

    @Column(name = "hash_friend_request")
    String hashFriendRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    FriendRequestStatus friendRequestStatus;

    @Column(name = "created_at", updatable = false)
    Instant createdAt;
}
