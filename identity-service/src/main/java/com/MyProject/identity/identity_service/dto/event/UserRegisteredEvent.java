package com.MyProject.identity.identity_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserRegisteredEvent {
    // Thông tin đăng ký cơ bản
    String eventId;
    String userId;
    String username;
    String email;
    String displayName;
    String firstName;
    String lastName;
    LocalDate dob;
    String phoneNumber;
    String city;
    LocalDateTime joinDate;

    // Thông tin cho OAuth2 register (nếu có)
    String generatedPassword;
    String resetPasswordToken;
    String resetPasswordUrl;
}
