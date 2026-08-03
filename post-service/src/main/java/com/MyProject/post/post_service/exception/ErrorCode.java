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
    BUSINESS_SCHEDULE_NOT_EXISTED(8303, "Business schedule not existed!", HttpStatus.BAD_REQUEST),
    TRAVEL_ITINERARY_NOT_EXISTED(8304, "Travel itinerary not existed!", HttpStatus.BAD_REQUEST),
    MAP_STRATEGY_EXCEPTION(8305, "Strategy got some problem!", HttpStatus.BAD_REQUEST),
    INVALID_UPDATE_POST(8306, "{attribute} must not be blank!", HttpStatus.BAD_REQUEST),
    POST_NOT_FOUND(8307, "Post not found!", HttpStatus.NOT_FOUND),
    JOB_EXECUTION_FAILED(8308, "Job execution failed!", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULER_FAILED(8309, "Scheduler failed!", HttpStatus.INTERNAL_SERVER_ERROR),
    SCHEDULER_EXCEPTION(8310, "Scheduler exception!", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_POST(8311, "Cannot cancel completed post!", HttpStatus.INTERNAL_SERVER_ERROR),
    DELETE_JOB(8312, "Delete job throw exception!", HttpStatus.INTERNAL_SERVER_ERROR),
    ACTION_NOT_FOUND(8313, "Action not found!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8314, "Rate limit exceeded!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8315, "Service unavailable!", HttpStatus.SERVICE_UNAVAILABLE);

    int code;
    String message;
    HttpStatusCode statusCode;

    public static ApiResponse<java.lang.Object> of(ErrorCode errorCode) {
        return ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ResponseEntity<ApiResponse<Object>> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatusCode()).body(of(errorCode));
    }
}
