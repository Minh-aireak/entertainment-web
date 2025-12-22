package com.MyProject.common_dto.event.entity;

import lombok.Getter;

@Getter
public enum MessageType {
    DELETED_FOR_SENDER,
    DELETED_FOR_EVERYONE,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    FILE,
    STICKER,
    POST;
}
