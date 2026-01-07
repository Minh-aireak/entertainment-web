package com.MyProject.identity.identity_service.controller;

import static org.mockito.Mockito.*;

import com.MyProject.common.dto.request.IntrospectRequest;
import com.MyProject.common.dto.response.IntrospectResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.request.LogoutRequest;
import com.MyProject.identity.identity_service.dto.request.RefreshRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.AuthenticationService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@WebMvcTest(AuthenticationController.class)
@Import(
        {SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class}
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationService authenticationService;

    AuthenticationRequest authenticationRequest;
    AuthenticationResponse authenticationResponse;
    IntrospectRequest introspectRequest;
    IntrospectResponse introspectResponse;
    LogoutRequest logoutRequest;
    RefreshRequest refreshRequest;
    ObjectMapper objectMapper;
    String code;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        authenticationRequest = AuthenticationRequest.builder()
                .username("aireak")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .build();

        authenticationResponse = AuthenticationResponse.builder()
                .token("123456789").build();

        introspectRequest = IntrospectRequest.builder().token("123456789").build();

        introspectResponse = IntrospectResponse.builder()
                .valid(true)
                .userId("AIREAK")
                .build();

        logoutRequest = LogoutRequest.builder().token("123456789").build();

        refreshRequest = RefreshRequest.builder().token("123456789").build();

        code = "100";
    }

    @Test
    void login_success() throws Exception {
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(authenticationRequest)).thenReturn(authenticationResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.token").value("123456789"));

        verify(authenticationService, times(1)).authentication(authenticationRequest);
    }

    @Test
    void login_usernameNull() throws Exception {
        authenticationRequest.setUsername(null);
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(authenticationRequest)).thenThrow(new AppException(ErrorCode.USERNAME_NOTNULL));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8016))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Username cannot be null!"));

        verify(authenticationService, never()).authentication(authenticationRequest);
    }

    @Test
    void login_passwordNull() throws Exception {
        authenticationRequest.setPassword(null);
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(authenticationRequest)).thenThrow(new AppException(ErrorCode.PASSWORD_NOTNULL));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8017))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password cannot be null!"));

        verify(authenticationService, never()).authentication(authenticationRequest);
    }

    @Test
    void login_userNotExisted() throws Exception {
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.USER_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8002))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));

        verify(authenticationService, times(1)).authentication(authenticationRequest);
    }

    @Test
    void login_userNotActive() throws Exception {
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.USER_NOT_ACTIVE));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8023))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));

        verify(authenticationService, times(1)).authentication(authenticationRequest);
    }

    @Test
    void login_passwordIncorrect() throws Exception {
        String content = objectMapper.writeValueAsString(authenticationRequest);

        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.PASSWORD_INCORRECT));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8010))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password incorrect!"));

        verify(authenticationService, times(1)).authentication(authenticationRequest);
    }

    @Test
    void introspect_token_success() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest)).thenReturn(introspectResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.valid").value("true"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.userId").value("AIREAK"));

        verify(authenticationService, times(1)).introspectResponse(introspectRequest);
    }

    @Test
    void introspect_verifyTokenFailed() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest)).thenThrow(new AppException(ErrorCode.VERIFY_TOKEN_FAILED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8020))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Verify token failed!"));

        verify(authenticationService, times(1)).introspectResponse(introspectRequest);
    }

    @Test
    void introspect_parseException() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest)).thenThrow(new AppException(ErrorCode.PARSE_EXCEPTION));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8021))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Parse exception!"));

        verify(authenticationService, times(1)).introspectResponse(introspectRequest);
    }

    @Test
    void introspect_tokenInvalid() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest)).thenThrow(new AppException(ErrorCode.TOKEN_INVALID));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8014))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token invalid!"));

        verify(authenticationService, times(1)).introspectResponse(any());
    }

    @Test
    void introspect_tokenAlreadyInvalidated() throws Exception {
        String content = objectMapper.writeValueAsString(introspectRequest);

        when(authenticationService.introspectResponse(introspectRequest))
                .thenThrow(new AppException(ErrorCode.TOKEN_ALREADY_INVALIDATED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8015))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token already invalidated!"));

        verify(authenticationService, times(1)).introspectResponse(any());
    }

    @Test
    @WithMockUser
    void logout_success() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        doNothing().when(authenticationService).logout(logoutRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000));

        verify(authenticationService, times(1)).logout(logoutRequest);
    }

    @Test
    @WithMockUser
    void logout_accessDenied() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        doThrow(new AppException(ErrorCode.ACCESS_DENIED)).when(authenticationService).logout(logoutRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8019))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token not owned by user!"));

        verify(authenticationService, times(1)).logout(logoutRequest);
    }

    @Test
    void logout_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(authenticationService, never()).logout(any());
    }

    @Test
    @WithMockUser
    void logout_weakKey() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        doThrow(new AppException(ErrorCode.WEAK_KEY)).when(authenticationService).logout(logoutRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8018))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Key length is weak!"));

        verify(authenticationService, times(1)).logout(logoutRequest);
    }

    @Test
    @WithMockUser
    void logout_tokenInvalid() throws Exception {
        String content = objectMapper.writeValueAsString(logoutRequest);

        doThrow(new AppException(ErrorCode.TOKEN_INVALID)).when(authenticationService).logout(logoutRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8014))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token invalid!"));

        verify(authenticationService, times(1)).logout(logoutRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_success() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        when(authenticationService.refreshToken(refreshRequest)).thenReturn(authenticationResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.token").value("123456789"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    void refreshToken_unAuthentication() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8011))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));

        verify(authenticationService, never()).refreshToken(any());
    }

    @Test
    @WithMockUser
    void refreshToken_weakKey() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AppException(ErrorCode.WEAK_KEY)).when(authenticationService).refreshToken(refreshRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8018))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Key length is weak!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_accessDenied() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AppException(ErrorCode.ACCESS_DENIED)).when(authenticationService).refreshToken(refreshRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8019))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token not owned by user!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_tokenInvalid() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AppException(ErrorCode.TOKEN_INVALID)).when(authenticationService).refreshToken(refreshRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8014))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token invalid!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_tokenAlreadyInvalidated() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        when(authenticationService.refreshToken(refreshRequest))
                .thenThrow(new AppException(ErrorCode.TOKEN_ALREADY_INVALIDATED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8015))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Token already invalidated!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_verifyTokenFailed() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        when(authenticationService.refreshToken(refreshRequest)).thenThrow(new AppException(ErrorCode.VERIFY_TOKEN_FAILED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8020))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Verify token failed!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_parseException() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        when(authenticationService.refreshToken(refreshRequest)).thenThrow(new AppException(ErrorCode.PARSE_EXCEPTION));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8021))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Parse exception!"));

        verify(authenticationService, times(1)).refreshToken(refreshRequest);
    }

    @Test
    @WithMockUser
    void refreshToken_signerToken() throws Exception {
        String content = objectMapper.writeValueAsString(refreshRequest);

        doThrow(new AppException(ErrorCode.SIGNER_EXCEPTION))
                .when(authenticationService)
                .refreshToken(refreshRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8022))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Signer key invalid!"));

        verify(authenticationService, times(1)).refreshToken(any());
    }

    @Test
    void outboundAuthenticate_success() throws Exception {

        when(authenticationService.outboundAuthenticate(code))
                .thenReturn(authenticationResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/outbound/google")
                        .param("code", code)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.token").value("123456789"));

        verify(authenticationService, times(1)).outboundAuthenticate(code);
    }

    @Test
    void outboundAuthenticate_roleNotExisted() throws Exception {

        doThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED))
                .when(authenticationService)
                .outboundAuthenticate(code);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/outbound/google")
                        .param("code", code)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8007))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Role not existed!"));

        verify(authenticationService, times(1)).outboundAuthenticate(code);
    }

    @Test
    @WithMockUser
    void outboundAuthenticate_signerToken() throws Exception {

        doThrow(new AppException(ErrorCode.SIGNER_EXCEPTION))
                .when(authenticationService)
                .outboundAuthenticate(code);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/outbound/google")
                        .param("code", code)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(8022))
                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Signer key invalid!"));

        verify(authenticationService, times(1)).outboundAuthenticate(code);
    }
}
