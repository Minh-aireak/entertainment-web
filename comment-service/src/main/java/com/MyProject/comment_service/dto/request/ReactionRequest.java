package com.MyProject.comment_service.dto.request;

import com.MyProject.comment_service.enums.CommentReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReactionRequest {
    CommentReactionType type;
}
