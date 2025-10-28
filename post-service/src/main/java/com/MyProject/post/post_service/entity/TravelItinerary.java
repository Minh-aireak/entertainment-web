package com.MyProject.post.post_service.entity;

import com.MyProject.post.post_service.dto.response.DataWeatherResponse;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(value = "post")
@TypeAlias("travel-itinerary")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TravelItinerary extends Post{
    @Field("start_position")
    DataWeatherResponse startPosition;

    @Field("end_position")
    DataWeatherResponse endPosition;

    public static TravelItineraryBuilder<?, ?> fromPost(Post p) {
        return TravelItinerary.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .displayName(p.getDisplayName())
                .avatar(p.getAvatar())
                .title(p.getTitle())
                .content(p.getContent())
                .startTime(p.getStartTime())
                .endTime(p.getEndTime())
                .createdDate(p.getCreatedDate())
                .status(p.getStatus());
    }
}
