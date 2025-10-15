package com.MyProject.friend_service.entity;

import lombok.Getter;

@Getter
public enum FriendRequestStatus {
    PENDING, ACCEPTED, REJECTED_FROM_SENDER, CANCELLED_FROM_RECEIVER
}
