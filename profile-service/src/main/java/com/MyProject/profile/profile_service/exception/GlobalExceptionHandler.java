package com.MyProject.profile.profile_service.exception;

import com.MyProject.common.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.Objects;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final String MIN_VALUE = "min";

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAppException(AppException exception) {
        return ErrorCode.toResponseEntity(exception.getErrorCode());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handlingMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {

        String enumKey = Objects.requireNonNull(exception.getFieldError()).getDefaultMessage();

        Map<String, Object> attributes;

        ErrorCode errorCode = ErrorCode.valueOf(enumKey);

        ConstraintViolation<?> constrainViolation =
                exception.getBindingResult().getAllErrors().getFirst().unwrap(ConstraintViolation.class);

        attributes = constrainViolation.getConstraintDescriptor().getAttributes();

        ApiResponse<Object> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(mapAttributes(errorCode.getMessage(), attributes));

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    private String mapAttributes(String message, Map<String, Object> attributes) {
        String minValue = String.valueOf(attributes.get(MIN_VALUE));
        return message.replace("{" + MIN_VALUE + "}", minValue);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAccessDeniedException() {
        return ErrorCode.toResponseEntity(ErrorCode.UNAUTHORIZED);
    }
}
