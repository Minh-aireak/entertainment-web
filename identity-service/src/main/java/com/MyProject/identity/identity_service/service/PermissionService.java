package com.MyProject.identity.identity_service.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.dto.request.PermissionCreationRequest;
import com.MyProject.identity.identity_service.dto.request.PermissionUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.PermissionMapper;
import com.MyProject.identity.identity_service.repository.PermissionRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {
    PermissionRepository permissionRepository;
    RoleRepository roleRepository;
    PermissionMapper permissionMapper;

    @Transactional(rollbackFor = Exception.class)
    public PermissionResponse createPermission(PermissionCreationRequest request) {
        if (permissionRepository.existsById(request.getName())) throw new AppException(ErrorCode.PERMISSION_EXISTED);

        Permission permission = permissionMapper.toPermission(request);

        return permissionMapper.toPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionResponse updatePermission(PermissionUpdateRequest request) {
        Permission permission = permissionRepository
                .findById(request.getName())
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_EXISTED));

        permissionMapper.update(permission, request);

        return permissionMapper.toPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(String name) {
        Permission permission = permissionRepository
                .findById(name)
                .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_EXISTED));

        List<String> listRoles = roleRepository.findAllByPermissionsContaining(permission).stream()
                        .map(Role::getName)
                        .toList();

        roleRepository.removePermissionFromRoles(name, listRoles);

        permissionRepository.deleteById(name);
    }
    
    public List<PermissionResponse> getAllPermissions() {
        return permissionMapper.toListPermissionResponse(permissionRepository.findAll());
    }
}
