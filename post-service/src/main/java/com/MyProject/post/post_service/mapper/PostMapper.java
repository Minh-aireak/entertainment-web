package com.MyProject.post.post_service.mapper;

import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PostMapper {
    ScheduleResponse toScheduleResponse(Post post);
}
