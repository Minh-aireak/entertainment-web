package com.MyProject.identity.identity_service.dto.request;

import com.MyProject.profile.profile_service.validator.DobConstraint;
import com.MyProject.profile.profile_service.validator.PhoneNumberConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserProfileCreationRequest {
    String username;
    String email;
    String fistName;

    @NotNull(message = "NAME_INVALID")
    @NotBlank(message = "NAME_INVALID")
    String lastName;

    @NotNull(message = "DOB_NOTNULL")
    @DobConstraint(min = 16, message = "INVALID_DOB")
    LocalDate dob;

    @NotNull
    @PhoneNumberConstraint(message = "INVALID_PHONENUMBER")
    String phoneNumber;

    String city;
}
