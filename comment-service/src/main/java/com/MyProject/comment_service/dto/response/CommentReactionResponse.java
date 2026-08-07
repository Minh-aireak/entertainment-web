package com.MyProject.comment_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentReactionResponse {
    String commentId;
    int likeCount;
    int loveCount;
    /** "LIKE" / "LOVE" / null when the toggle just removed the viewer's reaction. */
    String myReaction;
}
