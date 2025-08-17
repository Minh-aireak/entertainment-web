package com.MyProject.post.post_service.dto.response;

import com.MyProject.post.post_service.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiResponse<Object> {
    @Builder.Default
    int code = 1000;

    String message;

    Object result;

    public static ApiResponse<?> of(ErrorCode errorCode) {
        return ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ResponseEntity<ApiResponse<?>> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatusCode()).body(ApiResponse.of(errorCode));
    }
}
