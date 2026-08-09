package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.RoleMapper;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
    RoleRepository roleRepository;

    @Mock
    UserRepository userRepository;

    Role role;
    Role role2;
    RoleResponse roleResponse;
    RoleResponse roleResponse2;
    RoleCreationRequest creationRequest;
    RoleUpdateRequest updateRequest;

    @BeforeEach
    void initData(){
        role = Role.builder()
                .name("USER")
                .description("User role")
                .build();

        role2 = Role.builder()
                .name("USER")
                .description("User role updated")
                .build();

        roleResponse = RoleResponse.builder()
                .name("USER")
                .description("User role")
                .build();

        roleResponse2 = RoleResponse.builder()
                .name("USER")
                .description("User role updated")
                .build();

        creationRequest = RoleCreationRequest.builder()
                .name("USER")
                .description("User role")
                .build();

        updateRequest = RoleUpdateRequest.builder()
                .name("USER")
                .description("User role updated")
                .build();
    }

    @Test
    void createRole_success() {
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(roleMapper.toRole(creationRequest)).thenReturn(role);
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toRoleResponse(role)).thenReturn(roleResponse);

        var actual = roleService.createRole(creationRequest);

        assertThat(actual).isSameAs(roleResponse);
        assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse);

        verify(roleRepository).existsById(any());
        verify(roleMapper, times(1)).toRole(any());
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
        verify(roleRepository, never()).save(any());
        verify(roleMapper, never()).toRoleResponse(any());
    }

    @Test
    void updateRole_success(){
        when(roleRepository.findByName(updateRequest.getName())).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role2);
        when(roleMapper.toRoleResponse(role2)).thenReturn(roleResponse2);

        var actual = roleService.updateRole(updateRequest);

        assertThat(actual).isSameAs(roleResponse2);
        assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse2);

        verify(roleRepository, times(1)).findByName(any());
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
    void deleteRole_success(){
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role));

        roleService.deleteRole("USER");

        verify(roleRepository, times(1)).findById(any());
        verify(roleRepository, times(1)).delete(any());
    }

    @Test
    void deleteRole_roleInUse_throwsAndDoesNotDelete(){
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role));
        when(userRepository.existsByRolesContaining(role)).thenReturn(true);

        var exception = assertThrows(AppException.class, () -> roleService.deleteRole("USER"));

        assertEquals(ErrorCode.ROLE_IS_IN_USE, exception.getErrorCode());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void deleteRole_roleNotExisted(){
        when(roleRepository.findById("USER")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class, () -> roleService.deleteRole("USER"));

        assertEquals(ErrorCode.ROLE_NOT_EXISTED, exception.getErrorCode());

        verify(roleRepository, times(1)).findById(any());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void getAllRoles_success(){
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(roleMapper.toListRoleResponse(List.of(role))).thenReturn(List.of(roleResponse));

        var response = roleService.getAllRoles();

        assertThat(response).usingRecursiveComparison().isEqualTo(List.of(roleResponse));

        verify(roleRepository, times(1)).findAll();
        verify(roleMapper, times(1)).toListRoleResponse(any());
    }
}