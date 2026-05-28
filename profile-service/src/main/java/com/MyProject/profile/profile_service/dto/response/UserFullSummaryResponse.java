package com.MyProject.profile.profile_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserFullSummaryResponse {
    String userId;
    String username;
    String email;
    String displayName;
    String dob;
    String phoneNumber;
    String city;
    String joinDate;
    String avatar;
    Integer totalPosts;
    Integer totalFriends;
}
