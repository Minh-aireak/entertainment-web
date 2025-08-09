package com.MyProject.identity.identity_service.dto.request;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserUpdateRequest {
    String id;

    @Size(min = 8, message = "PASSWORD_INVALID")
    String password;
}
