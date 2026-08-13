package com.MyProject.post.post_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "post")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Post {
    @MongoId
    String id;
    PostType postType;

    @Indexed
    String userId;
    String title;
    String content;
    String backgroundColor;
    String feeling;
    LocalDateTime startTime;
    LocalDateTime endTime;
    LocalDateTime createdDate;
    LocalDateTime modifiedDate;
    List<String> listUsersJoin;
    long likeCount;

    List<String> imageFileIds;

    String watchRoomId;
    String watchFilmId;
    String watchEpisodeId;
    String watchInviteCode;
    String watchFilmTitle;
    String watchFilmThumbnailFileId;
    int watchParticipantCount;
}
