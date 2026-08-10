package com.MyProject.post.post_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostRequest {
    String postType;
    String title;
    String content;
    String backgroundColor;
    String feeling;
    LocalDateTime startTime;
    LocalDateTime endTime;
    List<String> listUsersJoin;
    List<String> imageFileIds;
    String watchRoomId;
    String watchFilmId;
    String watchEpisodeId;
    String watchInviteCode;
    String watchFilmTitle;
    String watchFilmThumbnailFileId;
}
