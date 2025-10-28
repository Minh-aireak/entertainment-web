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
@Table(name = "user-relationship",
        indexes = {
                @Index(name = "idx_hash_friend", columnList = "hash_friend")
        })
public class UserRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "user_id", nullable = false)
    String userId;

    @Column(name = "related_user_id", nullable = false)
    String relatedUserId;

    @Column(name = "hash_friend", nullable = false)
    String hashFriend;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_status", nullable = false)
    RelationshipStatus relationshipStatus;

    @Column(name = "created_date", nullable = false)
    Instant createdDate;
}
