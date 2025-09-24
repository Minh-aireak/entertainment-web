package com.MyProject.identity.identity_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserCreationRequest {
    @NotNull(message = "USERNAME_NOTNULL")
    @Size(min = 6, message = "USERNAME_INVALID")
    String username;

    @NotNull(message = "PASSWORD_NOTNULL")
    @Size(min = 8, message = "PASSWORD_INVALID")
    String password;

    @Email
    @NotNull
    String email;
}
