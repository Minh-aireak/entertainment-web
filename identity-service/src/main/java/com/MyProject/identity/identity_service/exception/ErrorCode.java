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
    USERNAME_EXISTED(1001, "User existed!", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1002, "User not existed!", HttpStatus.NOT_FOUND),
    USERNAME_INVALID(1003, "Username must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(1004, "Password must be at least {min} characters!", HttpStatus.BAD_REQUEST),
    ROLE_INVALID(1005, "Role creation request cannot be null", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(1006, "Role existed!", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED(1007, "Role not existed!", HttpStatus.NOT_FOUND),
    PERMISSION_EXISTED(1008, "Permission existed!", HttpStatus.BAD_REQUEST),
    PERMISSION_NOT_EXISTED(1009, "Permission not found!", HttpStatus.NOT_FOUND),
    TRIP_EXISTED(1010, "Trip existed!", HttpStatus.BAD_REQUEST),
    PASSWORD_INCORRECT(1011, "Password incorrect!", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1012, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1013, "You don't have permission!", HttpStatus.FORBIDDEN),
    NAME_INVALID(1014, "Name cannot be blank!", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(1015, "Token invalid!", HttpStatus.UNAUTHORIZED),
    TOKEN_ALREADY_INVALIDATED(1016, "Token already invalidated!", HttpStatus.BAD_REQUEST),
    USERNAME_NOTNULL(1017, "Username cannot be null!", HttpStatus.BAD_REQUEST),
    PASSWORD_NOTNULL(1018, "Password cannot be null!", HttpStatus.BAD_REQUEST),
    DOB_NOTNULL(1019, "Day of birth cannot be null!", HttpStatus.BAD_REQUEST),
    INVALID_DOB(1020, "You must be at least {min} years old!", HttpStatus.BAD_REQUEST),
    WEAK_KEY(1021, "Key length is weak!", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(1022, "Token not owned by user!", HttpStatus.FORBIDDEN);

    int code;
    String message;
    HttpStatusCode statusCode;
}
