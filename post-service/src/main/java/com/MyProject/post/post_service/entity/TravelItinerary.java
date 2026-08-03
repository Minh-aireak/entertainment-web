package com.MyProject.post.post_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Document(value = "post")
@TypeAlias("travel-itinerary")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TravelItinerary extends Post{

    public static TravelItineraryBuilder<?, ?> fromPost(Post p) {
        return TravelItinerary.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .title(p.getTitle())
                .content(p.getContent())
                .startTime(p.getStartTime())
                .endTime(p.getEndTime())
                .createdDate(p.getCreatedDate())
                .status(p.getStatus());
    }
}
