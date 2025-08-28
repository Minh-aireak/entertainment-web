package com.MyProject.post.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatusUpdateResponse
{
    String userId;
    String scheduleId;
    String title;
    String newStatus;
    String message;
}
