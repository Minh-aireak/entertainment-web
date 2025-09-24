package com.MyProject.post.post_service.mapper;

import com.MyProject.post.post_service.dto.request.ScheduleUpdateRequest;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.entity.TravelItinerary;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PostMapper {
    ScheduleResponse toScheduleResponse(Post post);
    ScheduleResponse toTravelItineraryResponse(TravelItinerary travelItinerary);

    void updateBusinessSchedule(@MappingTarget Post post, ScheduleUpdateRequest request);
    void updateTravelItinerary(@MappingTarget TravelItinerary travelItinerary, ScheduleUpdateRequest request);
}
