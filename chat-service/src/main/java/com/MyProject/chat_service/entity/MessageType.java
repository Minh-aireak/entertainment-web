package com.MyProject.chat_service.entity;

import lombok.Getter;

@Getter
public enum MessageType {

    TEXT(null),

    DELETED_FOR_EVERYONE("The message have been deleted"),

    IMAGE("Sent an image"),

    AUDIO("Sent an audio"),

    VIDEO("Sent a video"),

    FILE("Sent a file"),

    STICKER("Sent a sticker"),

    POST("Shared a post");

    private final String defaultContent;

    MessageType(String defaultContent) {
        this.defaultContent = defaultContent;
    }

    public boolean isAttachmentType() {
        return this != TEXT;
    }
}
