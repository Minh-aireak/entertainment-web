
package com.MyProject.notification.notification_service.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import com.MyProject.common.dto.response.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAppException(AppException exception) {
        return ErrorCode.toResponseEntity(exception.getErrorCode());
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAccessDeniedException() {
        return ErrorCode.toResponseEntity(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(value = RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Object>> handlingRequestNotPermittedException() {
        return ErrorCode.toResponseEntity(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @ExceptionHandler(value = CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Object>> handlingCallNotPermittedException() {
        return ErrorCode.toResponseEntity(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
