package com.MyProject.notification.notification_service.exception;

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
    UNAUTHENTICATED(8201, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8202, "Forbidden!", HttpStatus.UNAUTHORIZED),
    CANNOT_SEND_EMAIL(8203, "Cannot send email!", HttpStatus.BAD_REQUEST),
    MAP_STRATEGY_EXCEPTION(8204, "Notification strategy got some problem!", HttpStatus.BAD_REQUEST),
    BULK_USER_PROFILE(8205, "Bulk user profile service got some problem!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8206, "Rate limit exceeded!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8207, "Service unavailable!", HttpStatus.SERVICE_UNAVAILABLE);

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
