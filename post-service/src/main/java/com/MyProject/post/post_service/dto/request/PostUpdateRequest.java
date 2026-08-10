package com.MyProject.post.post_service.dto.request;

import com.MyProject.post.post_service.validator.DataUpdatePostConstraint;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostUpdateRequest {
    @DataUpdatePostConstraint(message = "INVALID_UPDATE_POST", attribute = "Title")
    String title;

    @DataUpdatePostConstraint(message = "INVALID_UPDATE_POST", attribute = "Content")
    String content;

    LocalDateTime startTime;
    LocalDateTime endTime;
    List<String> imageFileIds;
}
