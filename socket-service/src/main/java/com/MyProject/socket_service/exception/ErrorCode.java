package com.MyProject.socket_service.exception;

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
    UNAUTHENTICATED(8801, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8802, "Forbidden!", HttpStatus.UNAUTHORIZED),
    USERID_NOT_FOUND(8803, "User not found!", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED_SOCKET(8804, "Unauthenticated socket!", HttpStatus.BAD_REQUEST),
    JSON_PROCESSING(8805, "Json processing had problem!", HttpStatus.INTERNAL_SERVER_ERROR);

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
