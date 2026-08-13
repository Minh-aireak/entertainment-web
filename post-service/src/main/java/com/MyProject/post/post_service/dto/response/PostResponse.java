package com.MyProject.post.post_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostResponse {
    String id;
    String postType;
    String userId;
    String displayName;
    String avatar;
    String title;
    String content;
    String backgroundColor;
    String feeling;
    LocalDateTime startTime;
    LocalDateTime endTime;
    String createdDate;
    String modifiedDate;
    long likeCount;
    boolean liked;
    List<String> imageFileIds;
    List<String> imageUrls;
    String watchRoomId;
    String watchFilmId;
    String watchEpisodeId;
    String watchInviteCode;
    String watchFilmTitle;
    String watchFilmThumbnailFileId;
    String watchFilmThumbnailUrl;
    int watchParticipantCount;
}
