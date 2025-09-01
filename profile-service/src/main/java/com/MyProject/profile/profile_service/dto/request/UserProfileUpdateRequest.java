package com.MyProject.profile.profile_service.dto.request;

import com.MyProject.profile.profile_service.validator.DobConstraint;
import com.MyProject.profile.profile_service.validator.PhoneNumberConstraint;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserProfileUpdateRequest {
    String username;
    String firstName;

    @Email
    String email;

    @NotBlank(message = "DISPLAY_NAME_NOT_BLANK")
    String displayName;

    @NotBlank(message = "NAME_INVALID")
    String lastName;

    @DobConstraint(min = 16, message = "INVALID_DOB")
    LocalDate dob;

    @PhoneNumberConstraint(message = "INVALID_PHONE_NUMBER")
    String phoneNumber;

    String city;
}
