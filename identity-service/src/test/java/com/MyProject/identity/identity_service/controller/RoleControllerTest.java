package com.MyProject.identity.identity_service.controller;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.RoleService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@SpringBootTest
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
class RoleControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoleService roleService;

    RoleCreationRequest creationRequest;
    RoleUpdateRequest updateRequest;
    RoleResponse roleResponse;
    ObjectMapper objectMapper;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();
        PermissionResponse permission1 = new PermissionResponse("TEST_PERMISSION1", "Permission1 for test");

        creationRequest = RoleCreationRequest.builder()
                .name("TEST_ROLE")
                .description("Role for test")
                .permissions(Set.of("TEST_PERMISSION1"))
                .build();

        updateRequest = RoleUpdateRequest.builder()
                .description("Role for test updated")
                .permissions(Set.of("TEST_PERMISSION2"))
                .build();

        roleResponse = RoleResponse.builder()
                .name("TEST_ROLE")
                .description("Role for test")
                .permissions(Set.of(permission1))
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(roleService.createRole(creationRequest)).thenReturn(roleResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Role for test"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.permissions[0].name")
                        .value("TEST_PERMISSION1"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.permissions[0].description")
                        .value("Permission1 for test"));

        verify(roleService, times(1)).createRole(creationRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void createRole_unAuthority_return403() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).createRole(any());
    }

    @Test
    void createRole_unAuthentication_return401() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).createRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_roleExisted_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(roleService.createRole(any())).thenThrow(new AppException(ErrorCode.ROLE_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1006))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role existed!"));

        verify(roleService, times(1)).createRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_permissionNotExisted_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(roleService.createRole(any())).thenThrow(new AppException(ErrorCode.PERMISSION_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/roles/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1009))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Permission not found!"));

        verify(roleService, times(1)).createRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        roleResponse.setDescription("Role for test updated");
        roleResponse.setPermissions(Set.of(new PermissionResponse("TEST_PERMISSION2", "Permission2 for test")));

        when(roleService.updateRole("TEST_ROLE", updateRequest)).thenReturn(roleResponse);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles/TEST_ROLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Role for test updated"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.permissions[0].name")
                        .value("TEST_PERMISSION2"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.permissions[0].description")
                        .value("Permission2 for test"));

        verify(roleService, times(1)).updateRole("TEST_ROLE", updateRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void updateRole_unAuthority_return403() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles/TEST_ROLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).updateRole(any(), any());
    }

    @Test
    void updateRole_unAuthentication_return401() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles/TEST_ROLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).updateRole(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_roleNotExisted_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        when(roleService.updateRole(any(), any())).thenThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.put("/roles/TEST_ROLE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1007))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role not existed!"));

        verify(roleService, times(1)).updateRole(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRole_authority_success() throws Exception {
        doNothing().when(roleService).deleteRole("TEST_ROLE");

        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Deleted role success!"));

        verify(roleService, times(1)).deleteRole("TEST_ROLE");
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void deleteRole_unAuthority_return403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).deleteRole(any());
    }

    @Test
    void deleteRole_unAuthentication_return401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).deleteRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRole_roleNotExisted_returnAppException() throws Exception {
        doThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED)).when(roleService).deleteRole(any());

        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1007))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role not existed!"));

        verify(roleService, times(1)).deleteRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllRoles_validAuthority_success() throws Exception {
        when(roleService.getAllRoles()).thenReturn(List.of(roleResponse));

        mockMvc.perform(MockMvcRequestBuilders.get("/roles/read"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].name").value("TEST_ROLE"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("result[0].description").value("Role for test"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].permissions[0].name")
                        .value("TEST_PERMISSION1"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].permissions[0].description")
                        .value("Permission1 for test"));

        verify(roleService, times(1)).getAllRoles();
    }

    @Test
    @WithMockUser(authorities = "OtherRoles")
    void getAllRoles_unAuthority_return403() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.get("/roles/read"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).getAllRoles();
    }

    @Test
    void getAllRoles_unAuthentication_return401() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.get("/roles/read"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).getAllRoles();
    }
}
