package com.MyProject.post.post_service.exception;

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
    UNAUTHENTICATED(8301, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8302, "Forbidden!", HttpStatus.UNAUTHORIZED),
    INVALID_POST_TYPE(8303, "Invalid post type!", HttpStatus.BAD_REQUEST),
    IMAGE_POST_EMPTY_IDS(8304, "Image post requires at least one image!", HttpStatus.BAD_REQUEST),
    WATCH_POST_MISSING_ROOM(8305, "Watch post missing room info!", HttpStatus.BAD_REQUEST),
    FILE_SERVICE_ERROR(8306, "Cannot resolve file URLs", HttpStatus.SERVICE_UNAVAILABLE),
    MAP_STRATEGY_EXCEPTION(8307, "Strategy got some problem!", HttpStatus.BAD_REQUEST),
    INVALID_UPDATE_POST(8308, "{attribute} must not be blank!", HttpStatus.BAD_REQUEST),
    POST_NOT_FOUND(8309, "Post not found!", HttpStatus.NOT_FOUND),
    JOB_EXECUTION_FAILED(8310, "Job execution failed!", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULER_FAILED(8311, "Scheduler failed!", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULER_EXCEPTION(8312, "Scheduler exception!", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_POST(8313, "Cannot cancel completed post!", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_JOB(8314, "Delete job throw exception!", HttpStatus.INTERNAL_SERVER_ERROR),
    ACTION_NOT_FOUND(8315, "Action not found!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8316, "Rate limit exceeded!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8317, "Service unavailable!", HttpStatus.SERVICE_UNAVAILABLE);

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
