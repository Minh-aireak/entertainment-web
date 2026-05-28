package com.MyProject.friend.friend_service.entity;

import lombok.Getter;

@Getter
public enum RelationshipStatus {
    FRIEND, UNFRIEND, BLOCKED_FROM_SENDER, BLOCK_FROM_RECEIVER
}
