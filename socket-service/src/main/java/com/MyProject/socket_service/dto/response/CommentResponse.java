package com.MyProject.socket_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    String id;
    String sourceId;
    String userId;
    String content;
    String parentId;
    String topParentId;
    int likeCount;
    int loveCount;
    int replyCount;
    String type;
    String status;
    String durationCreatedDate;
    String avatar;
    String displayName;
    // Deliberately no "myReaction" field here: it is per-viewer and must never be broadcast to
    // a whole room - each client keeps its own myReaction from REST responses and merges
    // broadcasts on top without touching it. See useCommentSocket.ts on the frontend.
}
