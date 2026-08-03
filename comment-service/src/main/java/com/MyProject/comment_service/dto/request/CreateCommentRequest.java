package com.MyProject.comment_service.dto.request;

import com.MyProject.comment_service.enums.CommentType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCommentRequest {
    String sourceId;
    String content;
    CommentType type;
    String parentId;
    String topParentId;
}
