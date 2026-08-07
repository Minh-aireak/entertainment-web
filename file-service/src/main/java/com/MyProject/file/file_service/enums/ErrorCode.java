package com.MyProject.file.file_service.enums;

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
    UNAUTHENTICATED(8401, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8402, "You don't have permission!", HttpStatus.FORBIDDEN),
    FILE_UPLOAD(8403, "File upload has problemed!", HttpStatus.BAD_REQUEST),
    UPLOAD_ERROR(8406, "Upload error!", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND(8404, "File not found!", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR(8405, "Validation error!", HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(8407, "Invalid file type!", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(8408, "File too large!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8409, "Rate limit exceeded!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8410, "Service unavailable!", HttpStatus.SERVICE_UNAVAILABLE),
    UPLOAD_SESSION_NOT_FOUND(8411, "Upload session not found or expired!", HttpStatus.BAD_REQUEST),
    INVALID_UPLOAD_PARTS(8412, "One or more upload parts are missing an ETag!", HttpStatus.BAD_REQUEST);

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
