package com.MyProject.identity.identity_service.service;

import java.util.List;

import com.MyProject.identity.identity_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.RoleMapper;
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
    UserRepository userRepository;

    @Transactional(rollbackFor = Exception.class)
    public RoleResponse createRole(RoleCreationRequest request) {
        if (roleRepository.existsById(request.getName())) throw new AppException(ErrorCode.ROLE_EXISTED);

        var role = roleMapper.toRole(request);

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleResponse updateRole(RoleUpdateRequest request) {
        Role role = roleRepository.findByName(request.getName()).orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));
        roleMapper.update(role, request);

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(String name) {
        Role role = roleRepository.findById(name)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        boolean isUsed = userRepository.existsByRolesContaining(role);

        if (isUsed) {
            throw new AppException(ErrorCode.ROLE_IS_IN_USE);
        }

        roleRepository.delete(role);
    }

    @Transactional
    public List<RoleResponse> getAllRoles() {
        return roleMapper.toListRoleResponse(roleRepository.findAll());
    }
}