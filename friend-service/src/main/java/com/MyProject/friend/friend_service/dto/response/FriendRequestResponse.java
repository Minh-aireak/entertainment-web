package com.MyProject.friend.friend_service.dto.response;

import com.MyProject.friend.friend_service.entity.FriendRequestStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendRequestResponse {
    String senderId;
    String displayName;
    String avatar;
    FriendRequestStatus status;
}
