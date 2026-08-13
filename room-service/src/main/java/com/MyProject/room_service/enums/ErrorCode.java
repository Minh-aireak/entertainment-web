package com.MyProject.room_service.enums;

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
    UNAUTHENTICATED(9101, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(9102, "Only the room host can do that!", HttpStatus.FORBIDDEN),
    ROOM_NOT_FOUND(9103, "Room not found!", HttpStatus.NOT_FOUND),
    ROOM_CLOSED(9104, "This room has been closed!", HttpStatus.BAD_REQUEST),
    ROOM_ACCESS_DENIED(9105, "You don't have access to this room!", HttpStatus.FORBIDDEN),
    ROOM_FULL(9106, "This room is full!", HttpStatus.BAD_REQUEST),
    INVALID_FILM(9107, "Selected film could not be found!", HttpStatus.BAD_REQUEST),
    NOT_A_PARTICIPANT(9108, "You need to join this room first!", HttpStatus.FORBIDDEN),
    MESSAGE_CONTENT_EMPTY(9109, "Message content must not be empty!", HttpStatus.BAD_REQUEST),
    MESSAGE_CONTENT_TOO_LONG(9110, "Message content is too long!", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(9111, "Too many requests. Please try again later!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(9112, "Room service is temporarily unavailable. Please try again later!", HttpStatus.SERVICE_UNAVAILABLE),
    OUTBOX_SAVE_FAILED(9113, "Failed to persist outbox event. Transaction has been rolled back!", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_PLAYBACK_ACTION(9114, "Invalid playback action!", HttpStatus.BAD_REQUEST),
    ROOM_NAME_EMPTY(9115, "Room name must not be empty!", HttpStatus.BAD_REQUEST),
    INVALID_POSITION(9116, "Playback position is invalid!", HttpStatus.BAD_REQUEST),
    INVALID_PLAYBACK_RATE(9117, "Playback rate is not supported!", HttpStatus.BAD_REQUEST),
    EPISODE_NOT_IN_FILM(9118, "Episode does not belong to this room's film!", HttpStatus.BAD_REQUEST),
    PLAYBACK_UPDATE_CONFLICT(9119, "Playback update conflicted with a concurrent change. Please retry!", HttpStatus.CONFLICT),
    TOKEN_SIGNING_FAILED(9120, "Failed to issue room subscription token!", HttpStatus.INTERNAL_SERVER_ERROR),
    FILM_SERVICE_UNAVAILABLE(9121, "Film service is temporarily unavailable. Please try again later!", HttpStatus.SERVICE_UNAVAILABLE);

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
