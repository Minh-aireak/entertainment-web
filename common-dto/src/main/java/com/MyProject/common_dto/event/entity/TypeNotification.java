package com.MyProject.common_dto.event.entity;

import lombok.Getter;

@Getter
public enum TypeNotification {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    STATUS_CHANGE,
    POST_COMMENT,
    SOCIAL_LIKE,
    SOCIAL_COMMENT,
    SOCIAL_SHARE,
    FILM_LIKE,
    FILM_COMMENT,
    FILM_SHARE,
}
