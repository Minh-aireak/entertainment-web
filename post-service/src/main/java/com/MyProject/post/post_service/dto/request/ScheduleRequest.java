package com.MyProject.post.post_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScheduleRequest {
    String postType;
    String title;
    String content;
    LocalDateTime startTime;
    LocalDateTime endTime;
    String latStart;
    String lonStart;
    String latEnd;
    String lonEnd;
}
