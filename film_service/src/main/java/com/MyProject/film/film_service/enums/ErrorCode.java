package com.MyProject.film.film_service.enums;

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
    UNAUTHENTICATED(8901, "Unauthenticated!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(8902, "You don't have permission!", HttpStatus.FORBIDDEN),
    FILM_NOT_FOUND(8903, "Film not found!", HttpStatus.NOT_FOUND),
    FILM_ALREADY_FOLLOWED(8904, "Film already followed!", HttpStatus.BAD_REQUEST),
    FILM_NOT_FOLLOWED(8905, "Film not followed!", HttpStatus.BAD_REQUEST),
    ACTOR_NOT_FOUND(8906, "Actor not found!", HttpStatus.BAD_REQUEST),
    CAST_NOT_FOUND(8907, "Cast not found!", HttpStatus.BAD_REQUEST),
    DIRECTOR_NOT_FOUND(8908, "Director not found!", HttpStatus.BAD_REQUEST),
    COUNTRY_NOT_FOUND(8909, "Country not found!", HttpStatus.BAD_REQUEST),
    EPISODE_NOT_FOUND(8910, "Episode not found!", HttpStatus.BAD_REQUEST),
    GENRE_NOT_FOUND(8911, "Genre not found!", HttpStatus.BAD_REQUEST),
    INVALID_KEY(8912, "Uncategorized error", HttpStatus.BAD_REQUEST),
    NAME_REQUIRED(8913, "Name is required", HttpStatus.BAD_REQUEST),
    TITLE_REQUIRED(8914, "Title is required", HttpStatus.BAD_REQUEST),
    DESCRIPTION_REQUIRED(8915, "Description is required", HttpStatus.BAD_REQUEST),
    COUNTRY_REQUIRED(8916, "Country is required", HttpStatus.BAD_REQUEST),
    GENRES_REQUIRED(8917, "Genres are required", HttpStatus.BAD_REQUEST),
    DIRECTOR_REQUIRED(8918, "Director is required", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED(8919, "Rate limit exceeded!", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(8920, "Service unavailable!", HttpStatus.SERVICE_UNAVAILABLE);

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
