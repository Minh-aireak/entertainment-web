package com.MyProject.post.post_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostResponse {
   String id;
   String userId;
   String content;
   Instant createdDate;
   Instant modifiedDate;
}
