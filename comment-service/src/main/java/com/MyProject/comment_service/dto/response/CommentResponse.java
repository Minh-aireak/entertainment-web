package com.MyProject.comment_service.dto.response;

import com.MyProject.comment_service.enums.CommentStatus;
import com.MyProject.comment_service.enums.CommentType;
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
    CommentType type;
    CommentStatus status;
    String durationCreatedDate;
    String avatar;
    String displayName;
}


