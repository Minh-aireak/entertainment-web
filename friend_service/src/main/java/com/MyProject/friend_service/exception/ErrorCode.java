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
    USERID_NOT_FOUND(8705, "User not found!", HttpStatus.NOT_FOUND),
    ATTRIBUTE_NOT_NULL(8709, "{attribute} must not be null!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
