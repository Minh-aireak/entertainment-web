package com.MyProject.identity.identity_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationRequest {
    @NotNull(message = "USERNAME_NOTNULL")
    String username;

    @NotNull(message = "PASSWORD_NOTNULL")
    String password;
}
