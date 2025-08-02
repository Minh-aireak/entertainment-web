package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.PermissionCreationRequest;
import com.MyProject.identity.identity_service.dto.request.PermissionUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.mapper.PermissionMapper;
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
import static org.mockito.Mockito.*;

@SpringBootTest
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("/test.properties")
class PermissionServiceTest {
    @Autowired
    PermissionService permissionService;

    @MockBean
    PermissionMapper permissionMapper;

    @MockBean
    PermissionRepository permissionRepository;

    @MockBean
    RoleRepository roleRepository;

    Permission permission;
    PermissionResponse permissionResponse;
    PermissionCreationRequest creationRequest;
    PermissionUpdateRequest updateRequest;
    Role role;
    Role role2;

    @BeforeEach
    void initData(){
        permission = Permission.builder()
                .name("READ")
                .description("Read data")
                .build();

        permissionResponse = PermissionResponse.builder()
                .name("READ")
                .description("Read data")
                .build();

        creationRequest = PermissionCreationRequest.builder()
                .name("READ")
                .description("Read data")
                .build();

        updateRequest = PermissionUpdateRequest.builder()
                .description("Read data and trip information")
                .build();

        role = Role.builder()
                .name("ADMIN")
                .description("Admin role")
                .permissions(Set.of(permission))
                .build();

        role2 = Role.builder()
                .name("ADMIN")
                .description("Admin role")
                .build();
    }

    @Test
    void createPermission_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(PermissionService.class.getMethod("createPermission", PermissionCreationRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void createPermission_success() {
        when(permissionRepository.existsById(creationRequest.getName())).thenReturn(false);
        when(permissionMapper.toPermission(creationRequest)).thenReturn(permission);
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toPermissionResponse(permission)).thenReturn(permissionResponse);

        var actual = permissionService.createPermission(creationRequest);

        verify(permissionRepository).existsById(creationRequest.getName());
        verify(permissionMapper, times(1)).toPermission(creationRequest);
        verify(permissionRepository, times(1)).save(permission);
        verify(permissionMapper, times(1)).toPermissionResponse(permission);

        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(permissionResponse);
    }

    @Test
    void createPermission_permissionExisted_return1008(){
        when(permissionRepository.existsById(creationRequest.getName())).thenReturn(true);

        var exception = assertThrows(AppException.class,
                () -> permissionService.createPermission(creationRequest));

        verify(permissionRepository).existsById(creationRequest.getName());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1008);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Permission existed!");
    }

    @Test
    void updatePermission_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(PermissionService.class
                .getMethod("updatePermission", String.class, PermissionUpdateRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void updatePermission_success(){
        permissionResponse.setDescription("Read data and trip information");

        when(permissionRepository.findById("READ")).thenReturn(Optional.of(permission));
        doNothing().when(permissionMapper).update(permission, updateRequest);
        when(permissionRepository.save(permission)).thenReturn(permission);
        when(permissionMapper.toPermissionResponse(permission)).thenReturn(permissionResponse);

        var actual = permissionService.updatePermission("READ", updateRequest);

        verify(permissionRepository).findById("READ");
        verify(permissionMapper).update(permission, updateRequest);
        verify(permissionRepository).save(permission);
        verify(permissionMapper).toPermissionResponse(permission);

        Assertions.assertThat(actual.getDescription()).isEqualTo("Read data and trip information");
        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(permissionResponse);
    }

    @Test
    void updatePermission_permissionNotExisted_return1009(){
        when(permissionRepository.findById("READ")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> permissionService.updatePermission("READ", updateRequest));

        verify(permissionRepository).findById("READ");

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1009);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Permission not found!");
    }

    @Test
    void deletePermission_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
        Assertions.assertThat(PermissionService.class.getMethod("deletePermission", String.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void deletePermission_success(){
        when(permissionRepository.findById("READ")).thenReturn(Optional.of(permission));
        when(roleRepository.findAllByPermissionsContaining(permission))
                .thenReturn(List.of(role));
        doNothing().when(roleRepository).removePermissionFromRoles(any(), any());

        permissionService.deletePermission("READ");

        verify(permissionRepository).findById("READ");
        verify(roleRepository).findAllByPermissionsContaining(permission);
        verify(roleRepository).removePermissionFromRoles(any(), any());
    }

    @Test
    void deletePermission_permissionNotExisted_return1009(){
        when(permissionRepository.findById("READ")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> permissionService.deletePermission("READ"));

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1009);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Permission not found!");
    }

    @Test
    void getAllPermission_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(PermissionService.class.getMethod("getAllPermissions")
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void getAllPermission_success(){
        when(permissionRepository.findAll()).thenReturn(List.of(permission));
        when(permissionMapper.toListPermissionResponse(List.of(permission)))
                .thenReturn(List.of(permissionResponse));

        var response = permissionService.getAllPermissions();

        verify(permissionRepository).findAll();
        verify(permissionMapper).toListPermissionResponse(List.of(permission));

        Assertions.assertThat(response).usingRecursiveComparison()
                .isEqualTo(List.of(permissionResponse));
    }
}
