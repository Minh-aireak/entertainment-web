package com.MyProject.friend_service.mapper;

import com.MyProject.friend_service.dto.response.FriendResponse;
import com.MyProject.friend_service.entity.UserRelationship;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FriendMapper {
    FriendResponse toFriendResponse(UserRelationship userRelationship);
}
