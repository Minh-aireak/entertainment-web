package com.MyProject.chat_service.entity;

import lombok.Getter;

@Getter
public enum MessageStatus {
    SENT,
    DELIVERED,
    SEEN,
    FAILED;
}
