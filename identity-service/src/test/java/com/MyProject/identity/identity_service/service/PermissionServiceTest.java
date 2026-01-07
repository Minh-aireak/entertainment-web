package com.MyProject.identity.identity_service.service;

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

import org.assertj.core.api.Assertions;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {
    @InjectMocks
    PermissionService permissionService;

    @Mock
    PermissionMapper permissionMapper;

    @Mock
    PermissionRepository permissionRepository;

    @Mock
    RoleRepository roleRepository;

    Permission permission;
    PermissionResponse permissionResponse;
    PermissionCreationRequest creationRequest;
    PermissionUpdateRequest updateRequest;
    Role role;

    @BeforeEach
    void initData(){
        permission = Permission.builder()
                .name("ADD_FRIEND")
                .description("Add new friend")
                .build();

        permissionResponse = PermissionResponse.builder()
                .name("ADD_FRIEND")
                .description("Add new friend")
                .build();

        creationRequest = PermissionCreationRequest.builder()
                .name("ADD_FRIEND")
                .description("Add new friend")
                .build();

        updateRequest = PermissionUpdateRequest.builder()
                .name("ADD_FRIEND")
                .description("Add new friend and chat with other users")
                .build();

        role = Role.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of(permission))
                .build();
    }

    @Test
    void createPermission_success() {
        when(permissionRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(permissionMapper.toPermission(creationRequest)).thenReturn(permission);
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toPermissionResponse(permission)).thenReturn(permissionResponse);

        var actual = permissionService.createPermission(creationRequest);

        assertThat(actual).usingRecursiveComparison().isEqualTo(permissionResponse);

        verify(permissionRepository, times(1)).existsById(creationRequest.getName());
        verify(permissionMapper, times(1)).toPermission(creationRequest);
        verify(permissionRepository, times(1)).save(permission);
        verify(permissionMapper, times(1)).toPermissionResponse(permission);
    }

    @Test
    void createPermission_permissionExisted(){
        when(permissionRepository.existsById(creationRequest.getName())).thenReturn(true);

        var exception = assertThrows(AppException.class,
                () -> permissionService.createPermission(creationRequest));

        assertEquals(ErrorCode.PERMISSION_EXISTED, exception.getErrorCode());

        verify(permissionRepository).existsById(creationRequest.getName());
    }

    @Test
    void updatePermission_success(){
        permission.setDescription("Add new friend and chat with other users");
        permissionResponse.setDescription("Add new friend and chat with other users");

        when(permissionRepository.findById(updateRequest.getName())).thenReturn(Optional.of(permission));
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toPermissionResponse(permission)).thenReturn(permissionResponse);

        var actual = permissionService.updatePermission(updateRequest);

        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(permissionResponse);

        verify(permissionRepository, times(1)).findById(any());
        verify(permissionMapper, times(1)).update(any(), any());
        verify(permissionRepository, times(1)).save(any());
        verify(permissionMapper, times(1)).toPermissionResponse(any());
    }

    @Test
    void updatePermission_permissionNotExisted(){
        when(permissionRepository.findById(updateRequest.getName())).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> permissionService.updatePermission(updateRequest));

        assertEquals(ErrorCode.PERMISSION_NOT_EXISTED, exception.getErrorCode());

        verify(permissionRepository, times(1)).findById(any());
        verify(permissionMapper, never()).update(any(), any());
        verify(permissionRepository, never()).save(any());
        verify(permissionMapper, never()).toPermissionResponse(any());
    }

    @Test
    void deletePermission_success(){
        when(permissionRepository.findById("ADD_FRIEND")).thenReturn(Optional.of(permission));
        when(roleRepository.findAllByPermissionsContaining(permission)).thenReturn(List.of(role));

        permissionService.deletePermission("ADD_FRIEND");

        verify(permissionRepository, times(1)).findById(any());
        verify(roleRepository, times(1)).findAllByPermissionsContaining(any());
        verify(roleRepository, times(1)).removePermissionFromRoles(any(), any());
        verify(permissionRepository, times(1)).deleteById(any());
    }

    @Test
    void deletePermission_permissionNotExisted(){
        when(permissionRepository.findById("ADD_FRIEND")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> permissionService.deletePermission("ADD_FRIEND"));

        assertEquals(ErrorCode.PERMISSION_NOT_EXISTED, exception.getErrorCode());

        verify(permissionRepository, times(1)).findById(any());
        verify(roleRepository, never()).findAllByPermissionsContaining(any());
        verify(roleRepository, never()).removePermissionFromRoles(any(), any());
        verify(permissionRepository, never()).deleteById(any());
    }

    @Test
    void getAllPermission_success(){
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permissionMapper.toListPermissionResponse(List.of(permission))).thenReturn(List.of(permissionResponse));

        var response = permissionService.getAllPermissions();

        assertThat(response).usingRecursiveComparison().isEqualTo(List.of(permissionResponse));

        verify(permissionRepository, times(1)).findAll();
        verify(permissionMapper, times(1)).toListPermissionResponse(List.of(permission));
    }
}
