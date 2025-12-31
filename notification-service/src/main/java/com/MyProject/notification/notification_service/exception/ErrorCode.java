package com.MyProject.notification.notification_service.exception;

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
    UNAUTHENTICATED(8201, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8202, "Forbidden!", HttpStatus.UNAUTHORIZED),
    CANNOT_SEND_EMAIL(8203, "Cannot send email!", HttpStatus.BAD_REQUEST),
    MAP_STRATEGY_EXCEPTION(8204, "Notification strategy got some problem!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
