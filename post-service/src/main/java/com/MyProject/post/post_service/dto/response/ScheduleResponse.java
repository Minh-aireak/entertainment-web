package com.MyProject.post.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScheduleResponse {
   String id;
   String userId;
   String title;
   String content;
   LocalDateTime startTime;
   LocalDateTime endTime;
   String createdDate;
   String status;
}
