package com.MyProject.friend_service.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    UNAUTHENTICATED(8701, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8702, "Forbidden!", HttpStatus.UNAUTHORIZED),
    ALREADY_SEND_REQUEST(8703, "Friend request already sent!", HttpStatus.BAD_REQUEST),
    BULK_USER_PROFILE(8704, "Bulk data profile had error!", HttpStatus.BAD_REQUEST),
    USERID_NOT_FOUND(8705, "User not found!", HttpStatus.NOT_FOUND),
    HASH_FRIEND_REQUEST(8706, "Hash friend request not found!", HttpStatus.NOT_FOUND),
    HASH_FRIEND(8707, "Hash friend not found!", HttpStatus.NOT_FOUND),
    ATTRIBUTE_NOT_NULL(8709, "{attribute} must not be null!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
