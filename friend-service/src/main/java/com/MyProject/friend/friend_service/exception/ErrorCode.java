package com.MyProject.friend.friend_service.exception;

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
    UNAUTHENTICATED(8701, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8702, "Forbidden!", HttpStatus.UNAUTHORIZED),
    ALREADY_SEND_REQUEST(8703, "Friend request already sent!", HttpStatus.BAD_REQUEST),
    BULK_USER_PROFILE(8704, "Bulk data profile had error!", HttpStatus.BAD_REQUEST),
    USERID_NOT_FOUND(8705, "User not found!", HttpStatus.NOT_FOUND),
    HASH_FRIEND_REQUEST(8706, "Hash friend request not found!", HttpStatus.NOT_FOUND),
    HASH_FRIEND(8707, "Hash friend not found!", HttpStatus.NOT_FOUND),
    ATTRIBUTE_NOT_NULL(8709, "{attribute} must not be null!", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_NOT_FOUND(8710, "Friend request not found!", HttpStatus.NOT_FOUND),
    FRIEND_REQUEST_ALREADY_PROCESSED(8711, "Friend request already processed!", HttpStatus.BAD_REQUEST),
    INVALID_STATUS(8712, "Invalid status!", HttpStatus.BAD_REQUEST);

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
