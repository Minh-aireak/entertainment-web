package com.MyProject.identity.identity_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    ERROR_UNDETERMINED(9999, "Undetermined!", HttpStatus.INTERNAL_SERVER_ERROR),
    USERNAME_EXISTED(8001, "User existed!", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(8002, "User not existed!", HttpStatus.NOT_FOUND),
    USERNAME_INVALID(8003, "Username must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(8004, "Password must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    ROLE_INVALID(8005, "Role creation request cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(8006, "Role existed!", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED(8007, "Role not existed!", HttpStatus.NOT_FOUND),
    PERMISSION_EXISTED(8008, "Permission existed!", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_EXISTED(8009, "Permission not found!", HttpStatus.NOT_FOUND),
    PASSWORD_INCORRECT(8010, "Password incorrect!", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(8011, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8012, "You don't have permission!", HttpStatus.FORBIDDEN),
    NAME_INVALID(8013, "Name cannot be blank!", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(8014, "Token invalid!", HttpStatus.UNAUTHORIZED),
    TOKEN_ALREADY_INVALIDATED(8015, "Token already invalidated!", HttpStatus.BAD_REQUEST),
    USERNAME_NOTNULL(8016, "Username cannot be null!", HttpStatus.BAD_REQUEST),
    PASSWORD_NOTNULL(8017, "Password cannot be null!", HttpStatus.BAD_REQUEST),
    DOB_NOTNULL(8018, "Day of birth cannot be null!", HttpStatus.BAD_REQUEST),
    INVALID_DOB(8019, "You must be at least {min} years old!", HttpStatus.BAD_REQUEST),
    WEAK_KEY(8020, "Key length is weak!", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(8021, "Token not owned by user!", HttpStatus.FORBIDDEN),
    INVALID_PHONE_NUMBER(8022, "Phone number invalid!", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode statusCode;
}
