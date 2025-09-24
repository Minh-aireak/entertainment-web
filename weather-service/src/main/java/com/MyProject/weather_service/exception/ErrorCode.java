package com.MyProject.weather_service.exception;

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
    UNAUTHENTICATED(8501, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8502, "You don't have permission!", HttpStatus.FORBIDDEN),
    ACCESS_DENIED(8506, "Token not owned by user!", HttpStatus.FORBIDDEN);

    int code;
    String message;
    HttpStatusCode statusCode;
}
