package com.MyProject.film.film_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WatchProgressResponse {
    String filmId;
    String filmTitle;
    String thumbnailUrl;
    String thumbnailFileId;
    Boolean series;
    String episodeId;
    Integer episodeNumber;
    Integer totalEpisodes;
    int positionSeconds;
    int durationSeconds;
    Instant updatedAt;
}
