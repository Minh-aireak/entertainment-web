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
    TYPE_CONVERSATION_ERROR(8603, "Type conversation - Error!", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND(8604, "Conversation not found!", HttpStatus.NOT_FOUND),
    USERID_NOT_FOUND(8605, "User not found!", HttpStatus.NOT_FOUND),
    MAP_MESSAGE_ERROR(8606, "Map to message response have some problem!", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND(8607, "Message not found!", HttpStatus.NOT_FOUND),
    ATTRIBUTE_NOT_BLANK(8608, "{attribute} must not be blank!", HttpStatus.BAD_REQUEST),
    ATTRIBUTE_NOT_NULL(8609, "{attribute} must not be null!", HttpStatus.BAD_REQUEST),
    JSON_PROCESSING(8610, "Json processing had problem!", HttpStatus.PROCESSING);

    int code;
    String message;
    HttpStatusCode statusCode;
}
