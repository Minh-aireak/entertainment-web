package com.MyProject.friend_service.dto.request;

import com.MyProject.friend_service.entity.FriendRequestStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateFriendRequestStatus {
    String userId;
    FriendRequestStatus friendRequestStatus;
}
