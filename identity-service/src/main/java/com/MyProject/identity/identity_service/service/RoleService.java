package com.MyProject.identity.identity_service.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.RoleMapper;
import com.MyProject.identity.identity_service.repository.PermissionRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleService {
    RoleRepository roleRepository;
    RoleMapper roleMapper;
    PermissionRepository permissionRepository;

    @Transactional(rollbackFor = Exception.class)
    public RoleResponse createRole(RoleCreationRequest request) {
        if (roleRepository.existsById(request.getName())) throw new AppException(ErrorCode.ROLE_EXISTED);

        var role = roleMapper.toRole(request);

        var permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissions()));

        if (request.getPermissions().size() != permissions.size()) {
            throw new AppException(ErrorCode.PERMISSION_NOT_EXISTED);
        }

        role.setPermissions(permissions);

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleResponse updateRole(String name, RoleUpdateRequest request) {
        Role role = roleRepository.findByName(name).orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        if (request.getPermissions() != null) {
            var permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissions()));
            role.setPermissions(permissions);
        }
        roleMapper.update(role, request);

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String name) {
        if (roleRepository.existsById(name)) {
            roleRepository.deleteById(name);
        } else {
            throw new AppException(ErrorCode.ROLE_NOT_EXISTED);
        }
    }

    @Transactional
    public List<RoleResponse> getAllRoles() {
        return roleMapper.toListRoleResponse(roleRepository.findAll());
    }
}
