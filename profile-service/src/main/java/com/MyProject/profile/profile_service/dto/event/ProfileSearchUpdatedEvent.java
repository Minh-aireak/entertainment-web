
package com.MyProject.profile.profile_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileSearchUpdatedEvent {
    String eventId;
    String userId;
    String avatarFileId;
    String displayName;
    String username;
    String version; // For event versioning
}

