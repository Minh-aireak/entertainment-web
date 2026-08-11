package com.MyProject.comment_service.dto.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentReactionChangedEvent {
    String commentId;
    String sourceId;
    String parentId;
    String actorUserId;
    int likeCount;
    int loveCount;
    String myReaction;
}
