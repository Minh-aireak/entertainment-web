package com.MyProject.common.entity;

import lombok.Getter;

@Getter
public enum MessageStatus {
    SENT,
    DELIVERED,
    SEEN,
    FAILED;
}
