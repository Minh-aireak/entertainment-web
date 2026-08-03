package com.MyProject.common.exception;

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
    MAP_STRATEGY_EXCEPTION(8305, "Strategy got some problem!", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(8001, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8002, "Forbidden!", HttpStatus.UNAUTHORIZED),
    RATE_LIMIT_EXCEEDED(8003, "Too many requests to downstream service. Please try again later!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8004, "Service unavailable! Please try again later!", HttpStatus.SERVICE_UNAVAILABLE);

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
