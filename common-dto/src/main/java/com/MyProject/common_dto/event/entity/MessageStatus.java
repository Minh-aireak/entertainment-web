package com.MyProject.common_dto.event.entity;

import lombok.Getter;

@Getter
public enum MessageStatus {
    SENT,
    DELIVERED,
    SEEN,
    FAILED;
}
