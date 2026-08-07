package com.MyProject.profile.profile_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document("user_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile {
    @MongoId
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
    // File ID trong file-service (bucket B2 private) - URL hiển thị được resolve mới mỗi lần đọc
    // (xem UserProfileService.resolveAvatarUrl) để tránh presigned URL hết hạn sau ~1h.
    String avatarFileId;
}
