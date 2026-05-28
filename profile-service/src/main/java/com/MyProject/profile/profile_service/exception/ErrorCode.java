package com.MyProject.profile.profile_service.exception;

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
    UNAUTHENTICATED(8101, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8102, "You don't have permission!", HttpStatus.FORBIDDEN),
    NAME_INVALID(8103, "Name cannot be blank!", HttpStatus.BAD_REQUEST),
    DOB_NOTNULL(8104, "Day of birth cannot be null!", HttpStatus.BAD_REQUEST),
    INVALID_DOB(8105, "You must be at least {min} years old!", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(8106, "Token not owned by user!", HttpStatus.FORBIDDEN),
    PROFILE_NOT_FOUND(8107, "Profile not existed!", HttpStatus.NOT_FOUND),
    DISPLAY_NAME_NOT_BLANK(8108, "Display name cannot be blank!", HttpStatus.BAD_REQUEST),
    INVALID_PHONE_NUMBER(8109, "Phone number invalid!", HttpStatus.BAD_REQUEST ),
    EMAIL_INVALID(8110, "Email invalid!", HttpStatus.BAD_REQUEST);

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
