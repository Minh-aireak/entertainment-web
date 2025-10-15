package com.MyProject.friend_service.dto.request;

import com.MyProject.friend_service.entity.FriendRequest;
import com.MyProject.friend_service.entity.RelationshipStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateRelationshipStatus {
    FriendRequest friendRequest;
    RelationshipStatus friendStatus;
}
