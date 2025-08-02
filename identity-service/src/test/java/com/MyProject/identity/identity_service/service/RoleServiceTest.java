package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.mapper.RoleMapper;
import com.MyProject.identity.identity_service.repository.PermissionRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("/test.properties")
class RoleServiceTest {
    @Autowired
    RoleService roleService;

    @MockBean
    RoleMapper roleMapper;

    @MockBean
    PermissionRepository permissionRepository;

    @MockBean
    RoleRepository roleRepository;

    Permission permission;
    Permission permission2;
    PermissionResponse permissionResponse;
    PermissionResponse permissionResponse2;
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

        permissionResponse2 = PermissionResponse.builder()
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
                .description("User role updated")
                .permissions(Set.of("ADD_FRIENDS"))
                .build();
    }

    @Test
    void createRole_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(RoleService.class.getMethod("createRole", RoleCreationRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void createRole_success() {
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(roleMapper.toRole(creationRequest)).thenReturn(role);
        when(permissionRepository.findAllById(creationRequest.getPermissions())).thenReturn(List.of(permission));
        when(roleRepository.save(role)).thenReturn(role);
        when(roleMapper.toRoleResponse(role)).thenReturn(roleResponse);

        var actual = roleService.createRole(creationRequest);

        verify(roleRepository).existsById(creationRequest.getName());
        verify(roleMapper, times(1)).toRole(creationRequest);
        verify(permissionRepository, times(1)).findAllById(creationRequest.getPermissions());
        verify(roleRepository, times(1)).save(role);
        verify(roleMapper).toRoleResponse(role);

        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse);
    }

    @Test
    void createRole_roleExisted_return1006(){
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(true);

        var exception = assertThrows(AppException.class,
                () -> roleService.createRole(creationRequest));

        verify(roleRepository).existsById(creationRequest.getName());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1006);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Role existed!");
    }

    @Test
    void createRole_permissionNotExisted_return1009(){
        creationRequest.setPermissions(Set.of("CREATE_TRIP", "ADD_FRIENDS"));
        when(roleRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(roleMapper.toRole(creationRequest)).thenReturn(role);
        when(permissionRepository.findAllById(creationRequest.getPermissions())).thenReturn(List.of(permission));

        var exception = assertThrows(AppException.class,
                () -> roleService.createRole(creationRequest));

        verify(roleRepository).existsById(creationRequest.getName());
        verify(roleMapper, times(1)).toRole(creationRequest);
        verify(permissionRepository, times(1)).findAllById(creationRequest.getPermissions());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1009);
        Assertions.assertThat(exception.getErrorCode().getMessage())
                .isEqualTo("Permission not found!");
    }

    @Test
    void updateRole_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(RoleService.class.getMethod("updateRole", String.class, RoleUpdateRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void updateRole_success(){
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(updateRequest.getPermissions())).thenReturn(List.of(permission2));
        doNothing().when(roleMapper).update(role, updateRequest);
        when(roleRepository.save(role)).thenReturn(role2);
        when(roleMapper.toRoleResponse(role2)).thenReturn(roleResponse2);

        var actual = roleService.updateRole("USER", updateRequest);

        verify(roleRepository).findByName("USER");
        verify(permissionRepository, times(1)).findAllById(updateRequest.getPermissions());
        verify(roleMapper).update(role, updateRequest);
        verify(roleRepository).save(role);
        verify(roleMapper).toRoleResponse(role2);

        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse2);
    }

    @Test
    void updateRole_roleNotExisted_return1007(){
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> roleService.updateRole("USER", updateRequest));

        verify(roleRepository).findByName("USER");

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1007);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Role not existed!");
    }

    @Test
    void updateRole_permissionsNull_notCallPermissionRepository(){
        updateRequest.setPermissions(null);
        roleResponse2.setPermissions(Set.of(permissionResponse));
        role2.setPermissions(Set.of(permission));

        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
        doNothing().when(roleMapper).update(role, updateRequest);
        when(roleRepository.save(role)).thenReturn(role2);
        when(roleMapper.toRoleResponse(role2)).thenReturn(roleResponse2);

        var actual = roleService.updateRole("USER", updateRequest);

        verify(roleRepository).findByName("USER");
        verify(roleMapper, times(1)).update(role, updateRequest);
        verify(permissionRepository, never()).findAllById(any());
        verify(roleRepository).save(role);
        verify(roleMapper).toRoleResponse(role2);

        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(roleResponse2);
    }

    @Test
    void deleteRole_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
        Assertions.assertThat(RoleService.class.getMethod("deleteRole", String.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void deleteRole_success(){
        when(roleRepository.existsById("USER")).thenReturn(true);
        doNothing().when(roleRepository).deleteById("USER");

        roleService.deleteRole("USER");

        verify(roleRepository).existsById("USER");
        verify(roleRepository, times(1)).deleteById("USER");
    }

    @Test
    void deleteRole_roleNotExisted_return(){
        when(roleRepository.existsById("USER")).thenReturn(false);

        var exception = assertThrows(AppException.class, () -> roleService.deleteRole("USER"));

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1007);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Role not existed!");
    }

    @Test
    void getAllRoles_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(RoleService.class.getMethod("getAllRoles")
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void getAllUsers_success(){
        when(roleRepository.findAll()).thenReturn(List.of(role));
        when(roleMapper.toListRoleResponse(List.of(role))).thenReturn(List.of(roleResponse));

        var response = roleService.getAllRoles();

        verify(roleRepository).findAll();
        verify(roleMapper).toListRoleResponse(List.of(role));

        Assertions.assertThat(response).usingRecursiveComparison()
                .isEqualTo(List.of(roleResponse));
    }
}
