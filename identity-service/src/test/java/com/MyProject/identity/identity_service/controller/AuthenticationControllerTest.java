package com.MyProject.identity.identity_service.controller;

import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.identity.identity_service.configuration.SecurityConfig;
import com.MyProject.identity.identity_service.configuration.JwtAuthenticationConverterTestConfig;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.service.AuthenticationService;
import com.MyProject.identity.identity_service.service.IdentityApiRateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthenticationController now identifies callers via httpOnly access_token/refresh_token
 * cookies rather than a JSON token body - every /auth/** route is on SecurityConfig's public
 * endpoint list (auth itself is what issues the cookies), so none of these requests need a
 * pre-authenticated JWT.
 */
@WebMvcTest(AuthenticationController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        JwtAuthenticationConverterTestConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    IdentityApiRateLimitService identityApiRateLimitService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private AuthenticationRequest loginRequest() {
        return AuthenticationRequest.builder().username("aireak").password("REDACTED_LEGACY_CREDENTIAL").build();
    }

    // ---------- login ----------

    @Test
    void login_success_setsAccessAndRefreshTokenCookies() throws Exception {
        when(authenticationService.authentication(any())).thenReturn(
                AuthenticationResponse.builder().token("access-123").refreshToken("refresh-456").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("message").value("Login success!"))
                .andExpect(cookie().value("access_token", "access-123"))
                .andExpect(cookie().value("refresh_token", "refresh-456"))
                .andExpect(cookie().httpOnly("access_token", true));

        verify(identityApiRateLimitService).checkLogin("aireak");
    }

    @Test
    void login_missingUsername_returns400WithFieldMessage() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder().password("REDACTED_LEGACY_CREDENTIAL").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.USERNAME_NOTNULL.getCode()))
                .andExpect(jsonPath("message").value("Username cannot be null!"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void login_userNotExisted_returns404() throws Exception {
        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.USER_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.USER_NOT_EXISTED.getCode()));
    }

    @Test
    void login_passwordIncorrect_returns400() throws Exception {
        when(authenticationService.authentication(any())).thenThrow(new AppException(ErrorCode.PASSWORD_INCORRECT));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.PASSWORD_INCORRECT.getCode()));
    }

    // ---------- introspect ----------

    @Test
    void introspect_queryParamToken_returnsValidity() throws Exception {
        when(authenticationService.introspectResponse("token-abc")).thenReturn(
                IntrospectResponse.builder().valid(true).userId("user-1").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect").param("token", "token-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.valid").value(true))
                .andExpect(jsonPath("result.userId").value("user-1"));
    }

    @Test
    void introspect_noTokenParamOrCookie_returns401AccessTokenMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(ErrorCode.ACCESS_TOKEN_MISSING.getCode()));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void introspect_fallsBackToAccessTokenCookieWhenNoQueryParam() throws Exception {
        when(authenticationService.introspectResponse("cookie-token")).thenReturn(
                IntrospectResponse.builder().valid(true).userId("user-2").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/introspect")
                        .cookie(new Cookie("access_token", "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.userId").value("user-2"));
    }

    // ---------- logout ----------

    @Test
    void logout_withRefreshTokenCookie_callsServiceAndClearsCookies() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "refresh-456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("message").value("Logout success!"))
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));

        verify(authenticationService).logout("refresh-456");
    }

    @Test
    void logout_noRefreshTokenCookie_skipsServiceButStillReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout"))
                .andExpect(status().isOk());

        verifyNoInteractions(authenticationService);
    }

    // ---------- refresh-token ----------

    @Test
    void refresh_withValidCookie_setsNewCookies() throws Exception {
        when(authenticationService.refreshToken("old-refresh")).thenReturn(
                AuthenticationResponse.builder().token("new-access").refreshToken("new-refresh").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("access_token", "new-access"))
                .andExpect(cookie().value("refresh_token", "new-refresh"));
    }

    @Test
    void refresh_noCookie_throwsRefreshTokenMissing() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(ErrorCode.REFRESH_TOKEN_MISSING.getCode()));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void refresh_serviceRejectsInvalidToken_returns401() throws Exception {
        when(authenticationService.refreshToken("bad-token")).thenThrow(new AppException(ErrorCode.TOKEN_INVALID));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh-token")
                        .cookie(new Cookie("refresh_token", "bad-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(ErrorCode.TOKEN_INVALID.getCode()));
    }

    // ---------- outbound google ----------

    @Test
    void outboundAuthenticate_success_setsCookies() throws Exception {
        when(authenticationService.outboundAuthenticate("auth-code")).thenReturn(
                AuthenticationResponse.builder().token("access-1").refreshToken("refresh-1").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/outbound/google").param("code", "auth-code"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("access_token", "access-1"));
    }

    @Test
    void outboundAuthenticate_roleNotExisted_returns404() throws Exception {
        when(authenticationService.outboundAuthenticate("auth-code")).thenThrow(new AppException(ErrorCode.ROLE_NOT_EXISTED));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/outbound/google").param("code", "auth-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.ROLE_NOT_EXISTED.getCode()));
    }
}
