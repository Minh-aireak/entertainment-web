package com.MyProject.chat_service.enums;

import lombok.Getter;

@Getter
public enum MessageStatus {
    SENT,
    DELIVERED,
    SEEN,
    FAILED;
}
