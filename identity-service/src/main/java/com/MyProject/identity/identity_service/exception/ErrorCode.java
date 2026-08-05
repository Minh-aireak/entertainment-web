package com.MyProject.identity.identity_service.exception;

import com.MyProject.common.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    USERNAME_EXISTED(8001, "User existed!", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(8002, "User not existed!", HttpStatus.NOT_FOUND),
    USERNAME_INVALID(8003, "Username must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(8004, "Password must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    ROLE_INVALID(8005, "Role creation request cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(8006, "Role existed!", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED(8007, "Role not existed!", HttpStatus.NOT_FOUND),
    PASSWORD_INCORRECT(8010, "Password incorrect!", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(8011, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8012, "You don't have permission!", HttpStatus.FORBIDDEN),
    ERROR_UNDETERMINED(8013, "Undetermined!", HttpStatus.INTERNAL_SERVER_ERROR),
    TOKEN_INVALID(8014, "Token invalid!", HttpStatus.UNAUTHORIZED),
    TOKEN_ALREADY_INVALIDATED(8015, "Token already invalidated!", HttpStatus.BAD_REQUEST),
    USERNAME_NOTNULL(8016, "Username cannot be null!", HttpStatus.BAD_REQUEST),
    PASSWORD_NOTNULL(8017, "Password cannot be null!", HttpStatus.BAD_REQUEST),
    WEAK_KEY(8018, "Key length is weak!", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(8019, "Token not owned by user!", HttpStatus.FORBIDDEN),
    VERIFY_TOKEN_FAILED(8020, "Verify token failed!", HttpStatus.BAD_REQUEST),
    PARSE_EXCEPTION(8021, "Parse exception!", HttpStatus.BAD_REQUEST),
    SIGNER_EXCEPTION(8022, "Signer key invalid!", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_ACTIVE(8023, "User not existed!", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_EXISTED(8024, "Email not existed!", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN_RESET(8025, "Invalid token reset!", HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(8026, "Token expired!", HttpStatus.BAD_REQUEST),
    EMAIL_INVALID(8027, "Email invalid!", HttpStatus.BAD_REQUEST),
    ROLE_IS_IN_USE(8028, "Role is in use!", HttpStatus.BAD_REQUEST),
    ACCESS_TOKEN_MISSING(8029, "Access token not found!", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_MISSING(8030, "Refresh token not found!", HttpStatus.UNAUTHORIZED),
    RATE_LIMIT_EXCEEDED(8031, "Too many requests. Please try again later!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8032, "Identity service is temporarily unavailable. Please try again later!", HttpStatus.SERVICE_UNAVAILABLE);

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
