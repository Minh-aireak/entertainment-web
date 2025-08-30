package com.MyProject.post.post_service.exception;

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
    UNAUTHENTICATED(8301, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8302, "Forbidden!", HttpStatus.UNAUTHORIZED),
    SCHEDULE_NOT_EXISTED(8303, "Schedule not existed!", HttpStatus.BAD_REQUEST),
    MAP_STRATEGY_EXCEPTION(8304, "Strategy got some problem!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
