package com.MyProject.file.file_service.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    UNAUTHENTICATED(8401, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8402, "You don't have permission!", HttpStatus.FORBIDDEN),
    FILE_UPLOAD(8403, "File upload has problemed!", HttpStatus.BAD_REQUEST),
    FILE_NOT_FOUND(8404, "File not found!", HttpStatus.NOT_FOUND),
    VALIDATION_ERROR(8405, "Validation error!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
