package com.MyProject.profile.profile_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    String username;
    String firstName;
    String lastName;
    LocalDate dob;
    String phoneNumber;
    String city;
}
