package com.MyProject.identity.identity_service.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.request.IntrospectRequest;
import com.MyProject.identity.identity_service.dto.request.LogoutRequest;
import com.MyProject.identity.identity_service.dto.request.RefreshRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.AuthenticationService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@SpringBootTest
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
class AuthenticationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationService authenticationService;

    AuthenticationRequest request;
    AuthenticationResponse response;
    IntrospectRequest introspectRequest;
    IntrospectResponse introspectResponse;
    LogoutRequest logoutRequest;
    RefreshRequest refreshRequest;
    ObjectMapper objectMapper;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        request = AuthenticationRequest.builder()
                .username("NguyenTuanMinh")
                .password("1801062012")
                .build();

        response = AuthenticationResponse.builder().token("123456789").build();

        introspectRequest = IntrospectRequest.builder().token("123456789").build();

        introspectResponse = IntrospectResponse.builder().valid(true).build();

        logoutRequest = LogoutRequest.builder().token("123456789").build();

        refreshRequest = RefreshRequest.builder().token("123456789").build();
    }

    @Test
    void login_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(request);

        when(authenticationService.authentication(request)).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.token").value("123456789"));

        verify(authenticationService, times(1)).authentication(request);
    }

    @Test
    void login_userNotExisted_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(request);

        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.USER_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1002))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));

        verify(authenticationService, times(1)).authentication(any());
    }

    @Test
    void login_passwordIncorrect_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(request);

        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.PASSWORD_INCORRECT));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password incorrect!"));

        verify(authenticationService, times(1)).authentication(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void introspect_validToken_success() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest)).thenReturn(introspectResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.valid").value("true"));

        verify(authenticationService, times(1)).introspectResponse(introspectRequest);
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void introspect_unAuthority_return403() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(authenticationService, never()).introspectResponse(any());
    }

    @Test
    void introspect_unAuthentication_return401() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(authenticationService, never()).introspectResponse(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void introspect_tokenInvalid_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(request);

        when(authenticationService.introspectResponse(any())).thenThrow(new AppException(ErrorCode.TOKEN_INVALID));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1015))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token invalid!"));

        verify(authenticationService, times(1)).introspectResponse(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void introspect_tokenAlreadyInvalidated_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(request);

        when(authenticationService.introspectResponse(any()))
                .thenThrow(new AppException(ErrorCode.TOKEN_ALREADY_INVALIDATED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1016))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token already invalidated!"));

        verify(authenticationService, times(1)).introspectResponse(any());
    }

    @Test
    @WithMockUser
    void logout_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        Jwt mockJwt = Jwt.withTokenValue("mock_Token")
                .header("alg", "none")
                .subject("OtherUsers")
                .claim("scope", "READ")
                .build();

        doNothing().when(authenticationService).logout(logoutRequest, mockJwt);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000));

        verify(authenticationService, times(1)).logout(any(), any());
    }

    @Test
    void logout_withTokenNotBelongToUser_return403() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        Jwt mockJwt = Jwt.withTokenValue("mock_Token")
                .header("alg", "none")
                .subject("OtherUsers")
                .claim("scope", "READ")
                .build();

        doThrow(new AccessDeniedException("Token not owned by user!"))
                .when(authenticationService)
                .logout(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(authenticationService, times(1)).logout(any(), any());
    }

    @Test
    void logout_unAuthentication_return401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(authenticationService, never()).logout(any(), any());
    }

    @Test
    void refreshToken_validRequest_success() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        Jwt mockJwt = Jwt.withTokenValue("mock_Token")
                .header("alg", "none")
                .subject("NguyenTuanMinh")
                .claim("scope", "READ")
                .build();

        when(authenticationService.refreshToken(refreshRequest, mockJwt)).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.token").value("123456789"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest, mockJwt);
    }

    @Test
    void refreshToken_unAuthentication_return401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(authenticationService, never()).refreshToken(any(), any());
    }

    @Test
    @WithMockUser
    void refreshToken_userNotExisted_returnAppException() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AppException(ErrorCode.USER_NOT_EXISTED))
                .when(authenticationService)
                .refreshToken(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1002))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));

        verify(authenticationService, times(1)).refreshToken(any(), any());
    }

    @Test
    @WithMockUser
    void refreshToken_withTokenNotBelongToUser_return403() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AccessDeniedException("Token not owned by user!"))
                .when(authenticationService)
                .refreshToken(any(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));

        verify(authenticationService, times(1)).refreshToken(any(), any());
    }
}
