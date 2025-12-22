package com.MyProject.post.post_service.exception;

import com.MyProject.post.post_service.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.Objects;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final String ATTRIBUTE = "attribute";

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<java.lang.Object>> handlingAppException(AppException exception) {
        return ApiResponse.toResponseEntity(exception.getErrorCode());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<java.lang.Object>> handlingMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        Map<String, Object> attributes = null;
        String enumKey = exception.getFieldError().getDefaultMessage();
        ErrorCode errorCode = ErrorCode.valueOf(enumKey);

        var constraintViolation = exception.getBindingResult().getAllErrors().getFirst().unwrap(ConstraintViolation.class);
        attributes = constraintViolation.getConstraintDescriptor().getAttributes();

        ApiResponse<java.lang.Object> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(
                Objects.nonNull(attributes)
                        ? mapAttributes(errorCode.getMessage(), attributes)
                : errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    private String mapAttributes(String message, Map<String, Object> attributes) {
        String attribute = String.valueOf(attributes.get(ATTRIBUTE));
        return message.replace("{" + ATTRIBUTE + "}", attribute);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<java.lang.Object>> handlingAccessDeniedException(AccessDeniedException exception) {
        return ApiResponse.toResponseEntity(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(value = JobExecutionException.class)
    public ResponseEntity<ApiResponse<java.lang.Object>> handlingJobExecutionException(JobExecutionException exception) {
        return ApiResponse.toResponseEntity(ErrorCode.JOB_EXECUTION_FAILED);
    }

    @ExceptionHandler(value = SchedulerException.class)
    public ResponseEntity<ApiResponse<java.lang.Object>> handlingSchedulerException(SchedulerException exception) {
        return ApiResponse.toResponseEntity(ErrorCode.SCHEDULER_FAILED);
    }
}
