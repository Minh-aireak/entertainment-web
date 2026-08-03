
package com.MyProject.profile.profile_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileSocketUpdatedEvent {
    String eventId;
    String userId;
    String version; // For event versioning
}

