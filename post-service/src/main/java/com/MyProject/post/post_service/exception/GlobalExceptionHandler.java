//package com.MyProject.notification.notification_service.exception;
//
//import response.dto.com.MyProject.post.post_service.ApiResponse;
//import jakarta.validation.ConstraintViolation;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.web.bind.MethodArgumentNotValidException;
//import org.springframework.web.bind.annotation.ControllerAdvice;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//
//import java.util.Map;
//import java.util.Objects;
//
//@ControllerAdvice
//public class GlobalExceptionHandler {
//    private static final String MIN_VALUE = "min";
//
//    @ExceptionHandler(value = AppException.class)
//    public ResponseEntity<ApiResponse<?>> handlingAppException(AppException exception) {
//        return ApiResponse.toResponseEntity(exception.getErrorCode());
//    }
//
//    @ExceptionHandler(value = MethodArgumentNotValidException.class)
//    public ResponseEntity<ApiResponse<?>> handlingMethodArgumentNotValidException(
//            MethodArgumentNotValidException exception) {
//        String enumKey = exception.getFieldError().getDefaultMessage();
//
//        Map<String, Object> attributes = null;
//
//        ErrorCode errorCode = ErrorCode.valueOf(enumKey);
//
//        var constrainViolation =
//                exception.getBindingResult().getAllErrors().getFirst().unwrap(ConstraintViolation.class);
//
//        attributes = constrainViolation.getConstraintDescriptor().getAttributes();
//
//        ApiResponse<?> apiResponse = new ApiResponse<>();
//        apiResponse.setCode(errorCode.getCode());
//        apiResponse.setMessage(
//                Objects.nonNull(attributes)
//                        ? mapAttributes(errorCode.getMessage(), attributes)
//                        : errorCode.getMessage());
//
//        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
//    }
//
//    private String mapAttributes(String message, Map<String, Object> attributes) {
//        String minValue = String.valueOf(attributes.get(MIN_VALUE));
//        return message.replace("{" + MIN_VALUE + "}", minValue);
//    }
//
//    @ExceptionHandler(value = AccessDeniedException.class)
//    public ResponseEntity<ApiResponse<?>> handlingAccessDeniedException(AccessDeniedException exception) {
//        return ApiResponse.toResponseEntity(ErrorCode.UNAUTHORIZED);
//    }
//}
