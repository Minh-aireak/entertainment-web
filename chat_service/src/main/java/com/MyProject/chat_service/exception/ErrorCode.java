package com.MyProject.chat_service.exception;

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
    UNAUTHENTICATED(8601, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8602, "Forbidden!", HttpStatus.UNAUTHORIZED),
    TYPE_CONVERSATION_ERROR(8603, "Type conversation - Error!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
