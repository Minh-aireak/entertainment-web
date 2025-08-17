package com.MyProject.profile.profile_service.dto.request;

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
    String firstName;
    String lastName;
    LocalDate dob;
    String phoneNumber;
    String city;
}
