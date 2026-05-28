package com.MyProject.identity.identity_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import com.MyProject.identity.identity_service.configuration.CustomJwtDecoder;
import com.MyProject.identity.identity_service.configuration.JwtAuthenticationEntryPoint;
import com.MyProject.identity.identity_service.configuration.SecurityConfig;
import com.MyProject.identity.identity_service.dto.request.ForgotPasswordRequest;
import com.MyProject.identity.identity_service.dto.request.ResetPasswordRequest;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
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
import com.MyProject.identity.identity_service.dto.request.UserCreationRequest;
import com.MyProject.identity.identity_service.dto.request.ChangePasswordRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.service.UserService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(UserController.class)
@Import(
        {SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class}
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    UserCreationRequest creationRequest;
    ChangePasswordRequest changePasswordRequest;
    UserResponse userResponse;
    ObjectMapper objectMapper;
    ResetPasswordRequest resetPasswordRequest;
    ForgotPasswordRequest forgotPasswordRequest;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        creationRequest = UserCreationRequest.builder()
                .username("aireak")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .email("aireak@gmail.com")
                .build();

        changePasswordRequest = ChangePasswordRequest.builder()
                .oldPassword("REDACTED_LEGACY_CREDENTIAL")
                .newPassword("1801062010")
                .build();

        PermissionResponse addFriend = new PermissionResponse("ADD_FRIEND", "Add new friend");

        RoleResponse roleResponse = RoleResponse.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of(addFriend))
                .build();

        userResponse = UserResponse.builder()
                .id("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .roles(Set.of(roleResponse))
                .build();

        forgotPasswordRequest = ForgotPasswordRequest.builder()
                .email("aireak@gmail.com")
                .build();

        resetPasswordRequest = ResetPasswordRequest.builder()
                .token("123456789")
                .password("1801062010")
                .build();
    }

    @Test
    void createUser_success() throws Exception {
        String content = objectMapper.writeValueAsString(creationRequest);

        when(userService.createUser(any())).thenReturn(userResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.id").value("123456789"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("aireak"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.email").value("aireak@gmail.com"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.roles[0].name").value("USER"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.roles[0].description").value("User role"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.roles[0].permissions[0].name").value("ADD_FRIEND"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.roles[0].permissions[0].description").value("Add new friend"));

        verify(userService, times(1)).createUser(creationRequest);
    }

    @Test
    void createUser_usernameIsNull() throws Exception {
        creationRequest.setUsername(null);
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8016))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Username cannot be null!"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_usernameInvalidSize() throws Exception {
        creationRequest.setUsername("1232");
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8003))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Username must be at least 6 characters!"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_passwordIsNull() throws Exception {
        creationRequest.setPassword(null);
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8017))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password cannot be null!"));

        verify(userService, never()).createUser(any());
    }

    @Test
    void createUser_passwordInvalidSize() throws Exception {
        creationRequest.setPassword("1111111");
        String content = objectMapper.writeValueAsString(creationRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8004))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password must be at least 8 characters!"));

        verify(userService, never()).createUser(any());
    }

    @Test
    @WithMockUser
    void changePassword_success() throws Exception {
        String content = objectMapper.writeValueAsString(changePasswordRequest);

        doNothing().when(userService).changePassword(changePasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Change password success!"));

        verify(userService, times(1)).changePassword(changePasswordRequest);
    }

    @Test
    void changePassword_unAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/password"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(userService, never()).changePassword(any());
    }

    @Test
    @WithMockUser
    void changePassword_oldPasswordIncorrect() throws Exception {
        String content = objectMapper.writeValueAsString(changePasswordRequest);

        doThrow(new AppException(ErrorCode.PASSWORD_INCORRECT)).when(userService).changePassword(changePasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8010))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password incorrect!"));

        verify(userService, times(1)).changePassword(changePasswordRequest);
    }


    @Test
    @WithMockUser
    void changePassword_oldPasswordInvalidSize() throws Exception {
        changePasswordRequest.setOldPassword("1111111");
        String content = objectMapper.writeValueAsString(changePasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8004))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password must be at least 8 characters!"));

        verify(userService, never()).changePassword(changePasswordRequest);
    }

    @Test
    @WithMockUser
    void changePassword_newPasswordInvalidSize() throws Exception {
        changePasswordRequest.setNewPassword("1111111");
        String content = objectMapper.writeValueAsString(changePasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8004))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password must be at least 8 characters!"));

        verify(userService, never()).changePassword(changePasswordRequest);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disableUser_success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/{id}", "aireak"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User disabled successfully!"));

        verify(userService, times(1)).disableUser("aireak");
    }

    @Test
    @WithMockUser(authorities = "OtherRoles")
    void disableUser_unAuthority() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/users/{id}", "aireak"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disableUser_userNotExisted() throws Exception {
        doThrow(new AppException(ErrorCode.USER_NOT_EXISTED)).when(userService).disableUser("test_userId");

        mockMvc.perform(MockMvcRequestBuilders.put("/users/{id}", "test_userId"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8002))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));

        verify(userService, times(1)).disableUser("test_userId");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsers_success() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(userResponse));

        mockMvc.perform(MockMvcRequestBuilders.get("/users"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].id").value("123456789"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].username").value("aireak"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].email").value("aireak@gmail.com"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].roles[0].name").value("USER"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].roles[0].description").value("User role"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].roles[0].permissions[0].name").value("ADD_FRIEND"))
                .andExpect(MockMvcResultMatchers.jsonPath("result[0].roles[0].permissions[0].description").value("Add new friend"));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    @WithMockUser(authorities = "OtherRoles")
    void getAllUsers_unAuthority() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users"))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(userService, never()).getAllUsers();
    }

    @Test
    void getAllUsers_unAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/users"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(userService, never()).getAllUsers();
    }

    @Test
    void forgotPassword_success() throws Exception {
        String content = objectMapper.writeValueAsString(forgotPasswordRequest);

        when(userService.forgotPassword(forgotPasswordRequest)).thenReturn("Check your email: aireak@gmail.com");

        mockMvc.perform(MockMvcRequestBuilders.post("/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result").value("Check your email: aireak@gmail.com"));

        verify(userService, times(1)).forgotPassword(forgotPasswordRequest);
    }

    @Test
    void forgotPassword_emailInvalid() throws Exception {
        forgotPasswordRequest.setEmail("123");
        String content = objectMapper.writeValueAsString(forgotPasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8027))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Email invalid!"));

        verify(userService, never()).forgotPassword(forgotPasswordRequest);
    }

    @Test
    void forgotPassword_emailNotExisted() throws Exception {
        String content = objectMapper.writeValueAsString(forgotPasswordRequest);

        when(userService.forgotPassword(forgotPasswordRequest)).thenThrow(new AppException(ErrorCode.EMAIL_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/users/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8024))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Email not existed!"));

        verify(userService, times(1)).forgotPassword(forgotPasswordRequest);
    }

    @Test
    void resetPassword_success() throws Exception {
        String content = objectMapper.writeValueAsString(resetPasswordRequest);

        doNothing().when(userService).resetPassword(resetPasswordRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Reset password success!"));

        verify(userService, times(1)).resetPassword(resetPasswordRequest);
    }

}
