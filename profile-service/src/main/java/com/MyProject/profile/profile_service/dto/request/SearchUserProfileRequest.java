package com.MyProject.profile.profile_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class SearchUserProfileRequest {
    String displayName;
    int page;
    int size;
}
