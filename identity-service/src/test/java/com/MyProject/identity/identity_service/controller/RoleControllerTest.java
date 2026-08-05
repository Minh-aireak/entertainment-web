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
import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.RoleService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@WebMvcTest(RoleController.class)
@Import(
        {SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class}
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
class RoleControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoleService roleService;

    RoleCreationRequest creationRequest;
    RoleUpdateRequest updateRequest;
    RoleResponse roleResponse;
    ObjectMapper objectMapper;
    String nameRoleForDelete;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        creationRequest = RoleCreationRequest.builder()
                .name("TEST_ROLE")
                .description("Role for test")
                .build();

        updateRequest = RoleUpdateRequest.builder()
                .name("TEST_ROLE")
                .description("Role for test updated")
                .build();

        roleResponse = RoleResponse.builder()
                .name("TEST_ROLE")
                .description("Role for test")
                .build();

        nameRoleForDelete = "TEST_ROLE";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(roleService.createRole(creationRequest)).thenReturn(roleResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Role for test"));

        verify(roleService, times(1)).createRole(creationRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void createRole_unAuthority() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).createRole(any());
    }

    @Test
    void createRole_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).createRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRole_roleExisted() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(roleService.createRole(creationRequest)).thenThrow(new AppException(ErrorCode.ROLE_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8006))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role existed!"));

        verify(roleService, times(1)).createRole(creationRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_success() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        roleResponse.setDescription("Role for test updated");

        when(roleService.updateRole(updateRequest)).thenReturn(roleResponse);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.name").value("TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.description").value("Role for test updated"));

        verify(roleService, times(1)).updateRole(updateRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void updateRole_unAuthority() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).updateRole(updateRequest);
    }

    @Test
    void updateRole_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).updateRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_roleNotExisted() throws Exception {
        String content = objectMapper.writeValueAsString(updateRequest);

        when(roleService.updateRole(updateRequest)).thenThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.put("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8007))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role not existed!"));

        verify(roleService, times(1)).updateRole(updateRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRole_success() throws Exception {
        doNothing().when(roleService).deleteRole(nameRoleForDelete);

        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Deleted role success!"));

        verify(roleService, times(1)).deleteRole(nameRoleForDelete);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void deleteRole_unAuthority() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).deleteRole(any());
    }

    @Test
    void deleteRole_unAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).deleteRole(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRole_roleNotExisted() throws Exception {
        doThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED)).when(roleService).deleteRole(nameRoleForDelete);

        mockMvc.perform(MockMvcRequestBuilders.delete("/roles/TEST_ROLE"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8007))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role not existed!"));

        verify(roleService, times(1)).deleteRole(nameRoleForDelete);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllRoles_success() throws Exception {
        when(roleService.getAllRoles()).thenReturn(List.of(roleResponse));

        mockMvc.perform(MockMvcRequestBuilders.get("/roles"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].name").value("TEST_ROLE"))
                .andExpect(
                        MockMvcResultMatchers.jsonPath("result[0].description").value("Role for test"));

        verify(roleService, times(1)).getAllRoles();
    }

    @Test
    @WithMockUser(authorities = "OtherRoles")
    void getAllRoles_unAuthority() throws Exception {

        mockMvc.perform(MockMvcRequestBuilders.get("/roles"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(roleService, never()).getAllRoles();
    }

    @Test
    void getAllRoles_unAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/roles"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(roleService, never()).getAllRoles();
    }
}