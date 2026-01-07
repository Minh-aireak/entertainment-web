package com.MyProject.common.entity;

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
