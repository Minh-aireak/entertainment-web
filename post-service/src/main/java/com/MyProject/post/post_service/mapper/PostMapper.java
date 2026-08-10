package com.MyProject.post.post_service.mapper;

import com.MyProject.post.post_service.document.PostDoc;
import com.MyProject.post.post_service.dto.request.PostUpdateRequest;
import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PostMapper {
    PostResponse toPostResponse(Post post);

    @Mapping(target = "postType", expression = "java(post.getPostType().name())")
    PostDoc toPostDoc(Post post);

    void updatePost(@MappingTarget Post post, PostUpdateRequest request);
}
