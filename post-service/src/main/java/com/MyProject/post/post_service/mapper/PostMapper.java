package com.MyProject.post.post_service.mapper;

import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PostMapper {
    PostResponse toPostResponse(Post post);
}
