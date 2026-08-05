package com.MyProject.identity.identity_service.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Role;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoleMapper {
    Role toRole(RoleCreationRequest request);

    RoleResponse toRoleResponse(Role role);

    List<RoleResponse> toListRoleResponse(List<Role> roles);

    void update(@MappingTarget Role role, RoleUpdateRequest request);
}