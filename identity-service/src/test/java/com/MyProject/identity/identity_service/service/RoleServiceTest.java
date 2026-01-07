package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.RoleMapper;
import com.MyProject.identity.identity_service.repository.PermissionRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    @InjectMocks
    RoleService roleService;

    @Mock
    RoleMapper roleMapper;

    @Mock
    PermissionRepository permissionRepository;

    @Mock
    RoleRepository roleRepository;

    Permission permission;
    Permission permission2;
    PermissionResponse permissionResponse;
    Role role;
    Role role2;
    RoleResponse roleResponse;
    RoleResponse roleResponse2;
    RoleCreationRequest creationRequest;
    RoleUpdateRequest updateRequest;

    @BeforeEach
    void initData(){
        permission = Permission.builder()
                .name("CREATE_TRIP")
                .description("Create the trip")
                .build();

        permission2 = Permission.builder()
                .name("ADD_FRIENDS")
                .description("Can add friends")
                .build();

        permissionResponse = PermissionResponse.builder()
                .name("CREATE_TRIP")
                .description("Create the trip")
                .build();

        PermissionResponse permissionResponse2 = PermissionResponse.builder()
                .name("ADD_FRIENDS")
                .description("Can add friends")
                .build();

        role = Role.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of(permission))
                .build();

        role2 = Role.builder()
                .name("USER")
                .description("User role updated")
                .permissions(Set.of(permission2))
                .build();

        roleResponse = RoleResponse.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of(permissionResponse))
                .build();

        roleResponse2 = RoleResponse.builder()
                .name("USER")
                .description("User role updated")
                .permissions(Set.of(permissionResponse2))
                .build();

        creationRequest = RoleCreationRequest.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of("CREATE_TRIP"))
                .build();

        updateRequest = RoleUpdateRequest.builder()
                .name("USER")
                .description("User role updated")
                .permissions(Set.of("ADD_FRIENDS"))
                .build();
    }

    @Test
    void createRole_success() {
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(roleMapper.toRole(creationRequest)).thenReturn(role);
        when(permissionRepository.findAllById(creationRequest.getPermissions())).thenReturn(List.of(permission));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toRoleResponse(role)).thenReturn(roleResponse);

        var actual = roleService.createRole(creationRequest);

        assertThat(actual).isSameAs(roleResponse);
        assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse);

        verify(roleRepository).existsById(any());
        verify(roleMapper, times(1)).toRole(any());
        verify(permissionRepository, times(1)).findAllById(any());
        verify(roleRepository, times(1)).save(any());
        verify(roleMapper).toRoleResponse(any());
    }

    @Test
    void createRole_roleExisted(){
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(true);

        var exception = assertThrows(AppException.class,
                () -> roleService.createRole(creationRequest));

        assertEquals(ErrorCode.ROLE_EXISTED, exception.getErrorCode());

        verify(roleRepository, times(1)).existsById(any());
        verify(roleMapper, never()).toRole(any());
        verify(permissionRepository, never()).findAllById(any());
        verify(roleRepository, never()).save(any());
        verify(roleMapper, never()).toRoleResponse(any());
    }

    @Test
    void createRole_permissionNotExisted() {
        creationRequest.setPermissions(Set.of("CREATE_TRIP", "ADD_FRIENDS"));
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(roleMapper.toRole(creationRequest)).thenReturn(role);
        when(permissionRepository.findAllById(creationRequest.getPermissions())).thenReturn(List.of(permission));

        var exception = assertThrows(AppException.class,
                () -> roleService.createRole(creationRequest));

        assertEquals(ErrorCode.PERMISSION_NOT_EXISTED, exception.getErrorCode());

        verify(roleRepository).existsById(any());
        verify(roleMapper, times(1)).toRole(any());
        verify(permissionRepository, times(1)).findAllById(any());
        verify(roleRepository, never()).save(any());
        verify(roleMapper, never()).toRoleResponse(any());
    }

    @Test
    void updateRole_success(){
        when(roleRepository.findByName(updateRequest.getName())).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(updateRequest.getPermissions())).thenReturn(List.of(permission2));
        when(roleRepository.save(role)).thenReturn(role2);
        when(roleMapper.toRoleResponse(role2)).thenReturn(roleResponse2);

        var actual = roleService.updateRole(updateRequest);

        assertThat(actual).isSameAs(roleResponse2);
        assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse2);

        verify(roleRepository, times(1)).findByName(any());
        verify(permissionRepository, times(1)).findAllById(any());
        verify(roleMapper, times(1)).update(any(), any());
        verify(roleRepository, times(1)).save(any());
        verify(roleMapper, times(1)).toRoleResponse(any());
    }

    @Test
    void updateRole_roleNotExisted(){
        when(roleRepository.findByName(updateRequest.getName())).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> roleService.updateRole(updateRequest));

        assertEquals(ErrorCode.ROLE_NOT_EXISTED, exception.getErrorCode());

        verify(roleRepository, times(1)).findByName(any());
    }

    @Test
    void updateRole_permissionsNull(){
        updateRequest.setPermissions(null);
        roleResponse2.setPermissions(Set.of(permissionResponse));
        role2.setPermissions(Set.of(permission));

        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role2);
        when(roleMapper.toRoleResponse(role2)).thenReturn(roleResponse2);

        var actual = roleService.updateRole(updateRequest);

        assertThat(actual).isSameAs(roleResponse2);
        assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse2);

        verify(roleRepository, times(1)).findByName(any());
        verify(roleMapper, times(1)).update(any(), any());
        verify(permissionRepository, never()).findAllById(any());
        verify(roleRepository, times(1)).save(any());
        verify(roleMapper, times(1)).toRoleResponse(any());
    }

    @Test
    void deleteRole_success(){
        when(roleRepository.existsById("USER")).thenReturn(true);

        roleService.deleteRole("USER");

        verify(roleRepository, times(1)).existsById(any());
        verify(roleRepository, times(1)).deleteById(any());
    }

    @Test
    void deleteRole_roleNotExisted(){
        when(roleRepository.existsById("USER")).thenReturn(false);

        var exception = assertThrows(AppException.class, () -> roleService.deleteRole("USER"));

        assertEquals(ErrorCode.ROLE_NOT_EXISTED, exception.getErrorCode());

        verify(roleRepository, times(1)).existsById(any());
        verify(roleRepository, never()).deleteById(any());
    }

    @Test
    void getAllUsers_success(){
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(roleMapper.toListRoleResponse(List.of(role))).thenReturn(List.of(roleResponse));

        var response = roleService.getAllRoles();

        assertThat(response).usingRecursiveComparison().isEqualTo(List.of(roleResponse));

        verify(roleRepository, times(1)).findAll();
        verify(roleMapper, times(1)).toListRoleResponse(any());
    }
}

