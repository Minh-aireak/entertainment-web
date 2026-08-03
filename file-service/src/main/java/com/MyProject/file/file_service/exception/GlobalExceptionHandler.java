package com.MyProject.file.file_service.exception;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.file.file_service.enums.ErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static com.MyProject.file.file_service.enums.ErrorCode.toResponseEntity;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAppException(AppException exception) {
        return toResponseEntity(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {

        if (exception.getFieldError() == null) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setCode(ErrorCode.VALIDATION_ERROR.getCode());
            response.setMessage(ErrorCode.VALIDATION_ERROR.getMessage());
            return ResponseEntity
                    .status(ErrorCode.VALIDATION_ERROR.getStatusCode())
                    .body(response);
        }

        String enumKey = exception.getFieldError().getDefaultMessage();
        ErrorCode errorCode = ErrorCode.valueOf(enumKey);

        ApiResponse<Object> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(apiResponse);
    }


    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAccessDeniedException() {
        return toResponseEntity(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(value = RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Object>> handlingRequestNotPermitted() {
        return toResponseEntity(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @ExceptionHandler(value = CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Object>> handlingCallNotPermittedException() {
        return toResponseEntity(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
