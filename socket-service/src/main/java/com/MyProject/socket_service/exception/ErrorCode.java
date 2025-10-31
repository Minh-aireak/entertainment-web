package com.MyProject.socket_service.exception;

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
    UNAUTHENTICATED(8990, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8991, "Forbidden!", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED_SOCKET(8992, "Unauthenticated socket!", HttpStatus.BAD_REQUEST),
    JSON_PROCESSING(8993, "Json processing had problem!", HttpStatus.PROCESSING);

    int code;
    String message;
    HttpStatusCode statusCode;
}
