package com.MyProject.common.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequestEvent {
    String fromUserId;
    String toUserId;
    String hashFriendRequest;
    Instant createdAt;
}
