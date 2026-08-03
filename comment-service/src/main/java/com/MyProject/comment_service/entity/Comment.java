package com.MyProject.comment_service.entity;

import com.MyProject.comment_service.enums.CommentStatus;
import com.MyProject.comment_service.enums.CommentType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "comments")
@CompoundIndex(
        name = "source_parent_status_created_idx",
        def = """
    {
      'sourceId': 1,
      'parentId': 1,
      'status': 1,
      'createdDate': -1
    }
    """
)
public class Comment {
    @Id
    String id;
    String sourceId;
    String userId;
    String content;
    String parentId;
    String topParentId;
    int likeCount;
    int replyCount;
    CommentStatus status;
    CommentType type;
    @CreatedDate
    Instant createdDate;
    @LastModifiedDate
    Instant modifiedDate;
}
