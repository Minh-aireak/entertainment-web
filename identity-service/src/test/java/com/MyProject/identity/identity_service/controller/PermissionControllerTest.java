package com.MyProject.identity.identity_service.controller;

import static org.mockito.Mockito.*;

import java.util.List;

import com.MyProject.identity.identity_service.configuration.CustomJwtDecoder;
import com.MyProject.identity.identity_service.configuration.JwtAuthenticationEntryPoint;
import com.MyProject.identity.identity_service.configuration.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.MyProject.identity.identity_service.dto.request.PermissionCreationRequest;
import com.MyProject.identity.identity_service.dto.request.PermissionUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.PermissionService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@WebMvcTest(PermissionController.class)
@Import(
        {SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class}
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
class PermissionControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PermissionService permissionService;

    PermissionCreationRequest creationRequest;
    PermissionUpdateRequest updateRequest;
    PermissionResponse permissionResponse;
    ObjectMapper objectMapper;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        creationRequest = PermissionCreationRequest.builder()
                .name("TEST_PERMISSION")
                .description("Permission for test")
                .build();

        updateRequest = PermissionUpdateRequest.builder()
                .name("TEST_PERMISSION")
                .description("Permission for test updated")
                .build();

        permissionResponse = PermissionResponse.builder()
                .name("TEST_PERMISSION")
                .description("Permission for test")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createPermission_success() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(permissionService.createPermission(creationRequest)).thenReturn(permissionResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Permission for test"));

        verify(permissionService, times(1)).createPermission(creationRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void createPermission_unAuthority() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(permissionService, never()).createPermission(creationRequest);
    }

    @Test
    void createPermission_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(permissionService, never()).createPermission(creationRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_permissionExisted() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(permissionService.createPermission(creationRequest)).thenThrow(new AppException(ErrorCode.PERMISSION_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8008))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Permission existed!"));

        verify(permissionService, times(1)).createPermission(creationRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updatePermission_success() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        permissionResponse.setDescription("Permission for test updated");

        when(permissionService.updatePermission(updateRequest))
                .thenReturn(permissionResponse);

        mockMvc.perform(MockMvcRequestBuilders.put("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Permission for test updated"));

        verify(permissionService, times(1)).updatePermission(updateRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void updatePermission_unAuthority() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(permissionService, never()).updatePermission(updateRequest);
    }

    @Test
    void updatePermission_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(permissionService, never()).updatePermission(updateRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_permissionNotExisted() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        when(permissionService.updatePermission(updateRequest))
                .thenThrow(new AppException(ErrorCode.PERMISSION_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.put("/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8009))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Permission not found!"));

        verify(permissionService, times(1)).updatePermission(updateRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletePermission_success() throws Exception {
        doNothing().when(permissionService).deletePermission("TEST_PERMISSION");

        mockMvc.perform(MockMvcRequestBuilders.delete("/permissions/TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Deleted permission success!"));

        verify(permissionService, times(1)).deletePermission("TEST_PERMISSION");
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void deletePermission_unAuthority() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.delete("/permissions/TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(permissionService, never()).deletePermission(any());
    }

    @Test
    void deletePermission_unAuthentication() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.delete("/permissions/TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(permissionService, never()).deletePermission(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRole_permissionNotExisted() throws Exception {
        String value = "TEST_PERMISSION";
        doThrow(new AppException(ErrorCode.PERMISSION_NOT_EXISTED))
                .when(permissionService)
                .deletePermission(value);

        mockMvc.perform(MockMvcRequestBuilders.delete("/permissions/TEST_PERMISSION"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8009))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Permission not found!"));

        verify(permissionService, times(1)).deletePermission(value);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllPermissions_success() throws Exception {
        when(permissionService.getAllPermissions()).thenReturn(List.of(permissionResponse));

        mockMvc.perform(MockMvcRequestBuilders.get("/permissions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].name").value("TEST_PERMISSION"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("result[0].description").value("Permission for test"));

        verify(permissionService, times(1)).getAllPermissions();
    }

    @Test
    @WithMockUser(authorities = "OtherRoles")
    void getAllPermission_unAuthority() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.get("/permissions"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(permissionService, never()).getAllPermissions();
    }

    @Test
    void getAllPermissions_unAuthentication() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.get("/permissions"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(permissionService, never()).getAllPermissions();
    }
}
