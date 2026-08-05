package com.MyProject.chat_service.enums;

import com.MyProject.common.dto.response.ApiResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    UNAUTHENTICATED(8601, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8602, "Forbidden!", HttpStatus.FORBIDDEN),
    TYPE_CONVERSATION_ERROR(8603, "Type conversation - Error!", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND(8604, "Conversation not found!", HttpStatus.NOT_FOUND),
    USERID_NOT_FOUND(8605, "User not found!", HttpStatus.NOT_FOUND),
    MAP_MESSAGE_ERROR(8606, "Map to message response have some problem!", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND(8607, "Message not found!", HttpStatus.NOT_FOUND),
    CONTENT_REQUIRED(8608, "Content is required!", HttpStatus.BAD_REQUEST),
    ATTRIBUTE_NOT_NULL(8609, "{attribute} must not be null!", HttpStatus.BAD_REQUEST),
    ATTRIBUTE_NOT_BLANK(8620, "{attribute} must not be blank!", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED_SOCKET(8610, "Unauthenticated socket!", HttpStatus.BAD_REQUEST),
    JSON_PROCESSING(8611, "Json processing had problem!", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_REQUIRED(8612, "File is required!", HttpStatus.BAD_REQUEST),
    MESSAGE_TYPE_NOT_TEXT(8613, "Message type is not text!", HttpStatus.BAD_REQUEST),
    CONVERSATION_EXISTED(8614, "Conversation existed!", HttpStatus.BAD_REQUEST),
    UNCATEGORIZED_EXCEPTION(8615, "Uncategorized exception!", HttpStatus.INTERNAL_SERVER_ERROR),
    CONVERSATION_MEMBER_NOT_FOUND(8616, "Conversation member not found!", HttpStatus.NOT_FOUND),
    RATE_LIMIT_EXCEEDED(8617, "Too many requests. Please try again later!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8618, "Chat service is temporarily unavailable. Please try again later!", HttpStatus.SERVICE_UNAVAILABLE),
    OUTBOX_SAVE_FAILED(8619, "Failed to persist outbox event. Transaction has been rolled back!", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CONVERSATION_PARTICIPANTS(8621, "Conversation participants are invalid!", HttpStatus.BAD_REQUEST),
    KAFKA_PROCESSING_FAILED(8622, "Failed to process Kafka event", HttpStatus.INTERNAL_SERVER_ERROR),
    REPLY_TARGET_NOT_FOUND(8623, "Reply target message not found in this conversation!", HttpStatus.NOT_FOUND);

    int code;
    String message;
    HttpStatusCode statusCode;

    public static ApiResponse<Object> of(ErrorCode errorCode) {
        return ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ResponseEntity<ApiResponse<Object>> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatusCode()).body(of(errorCode));
    }
}
