package com.MyProject.film.film_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    String id;
    String sourceId;
    String userId;
    String content;
    String parentId;
    String topParentId;
    int likeCount;
    int replyCount;
    String durationCreatedDate;
}
