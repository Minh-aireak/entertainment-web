package com.MyProject.post.post_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ScheduleResponse {
   String id;
   String postType;
   String userId;
   String displayName;
   String avatar;
   String title;
   String content;
   LocalDateTime startTime;
   LocalDateTime endTime;
   String createdDate;
   String modifiedDate;
   String status;
   long likeCount;
   boolean liked;
}
