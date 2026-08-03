package com.MyProject.comment_service.mapper;

import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.dto.response.CommentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CommentMapper {
    @Mapping(target = "durationCreatedDate", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "displayName", ignore = true)
    CommentResponse toCommentResponse(Comment comment);
}
