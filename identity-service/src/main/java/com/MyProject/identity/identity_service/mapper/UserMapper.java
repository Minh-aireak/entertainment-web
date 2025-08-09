package com.MyProject.identity.identity_service.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.MyProject.identity.identity_service.dto.request.UserCreationRequest;
import com.MyProject.identity.identity_service.dto.request.UserUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.entity.User;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    List<UserResponse> toListUserResponse(List<User> users);

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void changePass (@MappingTarget User user, UserUpdateRequest request);
}
