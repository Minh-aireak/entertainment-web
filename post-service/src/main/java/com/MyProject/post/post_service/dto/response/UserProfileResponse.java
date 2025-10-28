package com.MyProject.post.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponse {
    String username;
    String email;
    String displayName;
    String avatar;
    String firstName;
    String lastName;
    LocalDate dob;
    String phoneNumber;
    String city;
    LocalDateTime joinDate;
}
