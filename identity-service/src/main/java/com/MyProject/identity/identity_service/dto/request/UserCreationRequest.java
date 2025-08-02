package com.MyProject.identity.identity_service.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.MyProject.identity.identity_service.validator.DobConstraint;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserCreationRequest {
    String id;

    @NotNull(message = "USERNAME_NOTNULL")
    @Size(min = 6, message = "USERNAME_INVALID")
    String username;

    @NotNull(message = "PASSWORD_NOTNULL")
    @Size(min = 8, message = "PASSWORD_INVALID")
    String password;

    String firstName;

    @NotNull(message = "NAME_INVALID")
    @NotBlank(message = "NAME_INVALID")
    String lastName;

    @NotNull(message = "DOB_NOTNULL")
    @DobConstraint(min = 18, message = "INVALID_DOB")
    LocalDate dob;
}
