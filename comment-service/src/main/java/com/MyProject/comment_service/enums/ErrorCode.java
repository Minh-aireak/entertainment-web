package com.MyProject.comment_service.enums;

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
    UNAUTHENTICATED(8901, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8902, "You don't have permission!", HttpStatus.FORBIDDEN),
    COMMENT_NOT_FOUND(8903, "Comment not found!", HttpStatus.NOT_FOUND),
    INVALID_COMMENT_TYPE(8904, "Invalid comment type!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8905, "Too many requests. Please try again later!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8906, "Comment service is temporarily unavailable. Please try again later!", HttpStatus.SERVICE_UNAVAILABLE),
    COMMENT_CONTENT_EMPTY(8907, "Comment content must not be empty!", HttpStatus.BAD_REQUEST),
    COMMENT_CONTENT_TOO_LONG(8908, "Comment content is too long!", HttpStatus.BAD_REQUEST),
    OUTBOX_SAVE_FAILED(8909, "Failed to persist outbox event. Transaction has been rolled back!", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REACTION_TYPE(8910, "Invalid reaction type!", HttpStatus.BAD_REQUEST);

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
