package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.dto.request.ExchangeTokenRequest;
import com.MyProject.identity.identity_service.dto.response.ExchangeTokenResponse;
import com.MyProject.identity.identity_service.dto.response.OutboundUserResponse;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    @InjectMocks
    AuthenticationService authenticationService;

    @Mock
    InvalidatedTokenRepository invalidatedTokenRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    OutboundIdentityClient outboundIdentityClient;

    @Mock
    OutboundUserClient outboundUserClient;

    @Mock
    RoleRepository roleRepository;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    String signerKey = "aireak-very-secret-key-512-bit-long-for-hs512-algorithm-test-only";
    User user;
    AuthenticationRequest authenticationRequest;
    Role role;

    @BeforeEach
    void initData() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", signerKey);
        ReflectionTestUtils.setField(authenticationService, "validDuration", 3600L);
        ReflectionTestUtils.setField(authenticationService, "refreshableDuration", 7200L);
        Permission permission = Permission.builder()
                .name("ADD_FRIEND")
                .description("Add new friends")
                .build();

        role = Role.builder()
                .name("USER")
                .description("User role")
                .permissions(Set.of(permission))
                .build();

        user = User.builder()
                .id("123456789")
                .username("aireak")
                .password("decoded")
                .roles(Set.of(role))
                .active(true)
                .build();

        authenticationRequest = AuthenticationRequest.builder()
                .username("aireak")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .build();
    }

    private void mockAuthenticatedUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("userId", "123456789")
                .build();

        JwtAuthenticationToken jwtAuthenticationToken =
                new JwtAuthenticationToken(jwt);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(jwtAuthenticationToken);
        SecurityContextHolder.setContext(securityContext);
    }

    private String createValidRefreshToken(String userId) throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("aireak")
                .issuer("aireak.com")
                .issueTime(new Date(System.currentTimeMillis() - 3600000))
                .expirationTime(new Date(System.currentTimeMillis() + 7200000))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "ROLE_USER ADD_FRIEND")
                .claim("userId", userId)
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        jwsObject.sign(new MACSigner(signerKey.getBytes()));

        return jwsObject.serialize();
    }

    private String createExpiredRefreshToken() throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("aireak")
                .issuer("aireak.com")
                .issueTime(new Date(System.currentTimeMillis() - 7200000))
                .expirationTime(new Date(System.currentTimeMillis() - 3600000))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "ROLE_USER ADD_FRIEND")
                .claim("userId", "123456789")
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        jwsObject.sign(new MACSigner(signerKey.getBytes()));

        return jwsObject.serialize();
    }

    private String createValidAccessToken(String userId) throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("aireak")
                .issuer("aireak.com")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "ROLE_USER ADD_FRIEND")
                .claim("userId", userId)
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        jwsObject.sign(new MACSigner(signerKey.getBytes()));

        return jwsObject.serialize();
    }

    @Test
    void authentication_success() throws ParseException {
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authenticationRequest.getPassword(), user.getPassword())).thenReturn(true);

        var response = authenticationService.authentication(authenticationRequest);

        String token = response.getToken();

        JWSObject jwsObject = JWSObject.parse(token);
        JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

        assertThat(claims.getSubject()).isEqualTo("aireak");
        assertThat(claims.getIssuer()).isEqualTo("aireak.com");
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();
        assertThat(expirationTime).isAfter(issueTime);
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getClaim("scope")).isEqualTo("ROLE_USER ADD_FRIEND");
        assertThat(claims.getClaim("userId")).isEqualTo("123456789");

        verify(userRepository, times(1)).findByUsername(anyString());
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void authentication_userNotExisted() {
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class, () -> authenticationService.authentication(authenticationRequest));

        assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void authentication_userNotActive(){
        user.setActive(false);
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));

        var exception = assertThrows(AppException.class, () -> authenticationService.authentication(authenticationRequest));

        assertEquals(ErrorCode.USER_NOT_ACTIVE, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void authentication_passwordIncorrect(){
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authenticationRequest.getPassword(), user.getPassword())).thenReturn(false);

        var exception = assertThrows(
                AppException.class,
                () -> authenticationService.authentication(authenticationRequest));

        assertEquals(ErrorCode.PASSWORD_INCORRECT, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername(any());
        verify(passwordEncoder, times(1)).matches(any(), any());
    }

    @Test
    void authentication_signerKeyException() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authenticationRequest.getPassword(), user.getPassword())).thenReturn(true);

        var exception = assertThrows(AppException.class,
                () -> authenticationService.authentication(authenticationRequest));

        assertEquals(ErrorCode.SIGNER_EXCEPTION, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername(anyString());
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void authentication_permissionEmpty() throws ParseException {
        role.setPermissions(null);
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authenticationRequest.getPassword(), user.getPassword())).thenReturn(true);

        var response = authenticationService.authentication(authenticationRequest);

        String token = response.getToken();

        JWSObject jwsObject = JWSObject.parse(token);
        JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

        assertThat(claims.getSubject()).isEqualTo("aireak");
        assertThat(claims.getIssuer()).isEqualTo("aireak.com");
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();
        assertThat(expirationTime).isAfter(issueTime);
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getClaim("scope")).isEqualTo("ROLE_USER");
        assertThat(claims.getClaim("userId")).isEqualTo("123456789");

        verify(userRepository, times(1)).findByUsername(anyString());
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void authentication_roleEmpty() throws ParseException {
        user.setRoles(null);
        when(userRepository.findByUsername(authenticationRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authenticationRequest.getPassword(), user.getPassword())).thenReturn(true);

        var response = authenticationService.authentication(authenticationRequest);

        String token = response.getToken();

        JWSObject jwsObject = JWSObject.parse(token);
        JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

        assertThat(claims.getSubject()).isEqualTo("aireak");
        assertThat(claims.getIssuer()).isEqualTo("aireak.com");
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();
        assertThat(expirationTime).isAfter(issueTime);
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getClaim("scope")).isEqualTo("");
        assertThat(claims.getClaim("userId")).isEqualTo("123456789");

        verify(userRepository, times(1)).findByUsername(anyString());
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void logout_success() throws Exception {
        mockAuthenticatedUser();
        String logoutToken = createValidAccessToken("123456789");
        com.MyProject.common.dto.request.TokenRequest request = new com.MyProject.common.dto.request.TokenRequest(logoutToken);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);

        authenticationService.logout(request);

        verify(invalidatedTokenRepository, times(1)).existsById(any());
        verify(invalidatedTokenRepository, times(1)).save(any());
    }

    @Test
    void logout_unAuthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        String logoutToken = createValidAccessToken("123456789");
        com.MyProject.common.dto.request.TokenRequest request = new com.MyProject.common.dto.request.TokenRequest(logoutToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.logout(request));

        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void logoutToken_accessDenied() throws Exception {
        mockAuthenticatedUser();
        String logoutToken = createValidAccessToken("12345678910");
        com.MyProject.common.dto.request.TokenRequest request = new com.MyProject.common.dto.request.TokenRequest(logoutToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.logout(request));

        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void logout_tokenInvalid() throws Exception {
        mockAuthenticatedUser();
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        String logoutToken = createValidAccessToken("123456789");
        com.MyProject.common.dto.request.TokenRequest request = new com.MyProject.common.dto.request.TokenRequest(logoutToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.logout(request));

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());

        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());
    }

    @Test
    void introspect_success() throws Exception {
        String token = createValidAccessToken("123456789");
        com.MyProject.common.dto.request.TokenRequest request = new com.MyProject.common.dto.request.TokenRequest(token);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);

        IntrospectResponse response = authenticationService.introspectResponse(request);

        assertTrue(response.isValid());
        assertEquals("123456789", response.getUserId());
    }

    @Test
    void introspect_expiredToken_tokenInvalid() throws Exception {
        mockAuthenticatedUser();
        String expiredToken = createExpiredRefreshToken();
        com.MyProject.common.dto.request.TokenRequest tokenRequest = com.MyProject.common.dto.request.TokenRequest.builder()
                .token(expiredToken)
                .build();

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(tokenRequest));

        verify(invalidatedTokenRepository, never()).existsById(any());

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void introspect_alreadyInvalidatedToken() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        com.MyProject.common.dto.request.TokenRequest tokenRequest = com.MyProject.common.dto.request.TokenRequest.builder()
                .token(refreshToken)
                .build();

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(true);

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(tokenRequest));

        assertEquals(ErrorCode.TOKEN_ALREADY_INVALIDATED, exception.getErrorCode());

        verify(invalidatedTokenRepository, times(1)).existsById(any());
    }

    @Test
    void introspect_verifyTokenFailed() throws Exception {
        mockAuthenticatedUser();
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        String token = createValidRefreshToken("123456789");
        com.MyProject.common.dto.request.TokenRequest tokenRequest = com.MyProject.common.dto.request.TokenRequest.builder()
                .token(token)
                .build();

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(tokenRequest));

        assertEquals(ErrorCode.VERIFY_TOKEN_FAILED, exception.getErrorCode());

        verify(invalidatedTokenRepository, never()).existsById(any());
    }

    @Test
    void refreshToken_success() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        TokenRequest request = new TokenRequest(refreshToken);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));

        AuthenticationResponse response = authenticationService.refreshToken(request);

        assertNotNull(response);
        assertNotNull(response.getToken());

        verify(invalidatedTokenRepository, times(1)).save(any());
        verify(invalidatedTokenRepository, times(1)).existsById(any());
        verify(userRepository, times(1)).findByUsername(any());
    }

    @Test
    void refreshToken_unAuthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        String refreshToken = createValidRefreshToken("123456789");
        TokenRequest request = new TokenRequest(refreshToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        verify(invalidatedTokenRepository, never()).save(any(InvalidatedToken.class));
        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(userRepository, never()).findByUsername(any());

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void refreshToken_accessDenied() throws Exception {
        String token = createValidRefreshToken("12345678910");
        mockAuthenticatedUser();
        TokenRequest request = new TokenRequest(token);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        verify(invalidatedTokenRepository, never()).save(any());
        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(userRepository, never()).findByUsername(any());

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void refreshToken_tokenInvalid() throws Exception {
        mockAuthenticatedUser();
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        String token = createValidRefreshToken("123456789");
        TokenRequest request = new TokenRequest(token);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        verify(invalidatedTokenRepository, never()).save(any());
        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(userRepository, never()).findByUsername(any());

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void refreshToken_expiredToken_tokenInvalid() throws Exception {
        mockAuthenticatedUser();
        String expiredToken = createExpiredRefreshToken();
        TokenRequest request = new TokenRequest(expiredToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        verify(invalidatedTokenRepository, never()).save(any());
        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(userRepository, never()).findByUsername(any());

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void refreshToken_alreadyInvalidatedToken() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        TokenRequest request = new TokenRequest(refreshToken);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(true);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertEquals(ErrorCode.TOKEN_ALREADY_INVALIDATED, exception.getErrorCode());

        verify(invalidatedTokenRepository, times(1)).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void refreshToken_userNotExisted() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        TokenRequest request = new TokenRequest(refreshToken);

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());

        verify(invalidatedTokenRepository, times(1)).existsById(any());
        verify(invalidatedTokenRepository, times(1)).save(any());
        verify(userRepository, times(1)).findByUsername(any());
    }

    @Test
    void outboundAuthenticate_success() throws Exception {
        ExchangeTokenResponse exchangeTokenResponse = ExchangeTokenResponse
                .builder()
                .accessToken("access_token")
                .build();

        OutboundUserResponse outboundUserResponse = OutboundUserResponse
                .builder()
                .id("123456789")
                .email("aireak@gmail.com")
                .build();

        when(outboundIdentityClient.exchangeToken(any(ExchangeTokenRequest.class))).thenReturn(exchangeTokenResponse);
        when(outboundUserClient.getInfo("json", exchangeTokenResponse.getAccessToken())).thenReturn(outboundUserResponse);
        when(userRepository.findByEmail(outboundUserResponse.getEmail())).thenReturn(Optional.ofNullable(user));

        var response = authenticationService.outboundAuthenticate("123");

        String token = response.getToken();

        JWSObject jwsObject = JWSObject.parse(token);
        JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

        assertThat(claims.getSubject()).isEqualTo("aireak");
        assertThat(claims.getIssuer()).isEqualTo("aireak.com");
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();
        assertThat(expirationTime).isAfter(issueTime);
        long duration = (expirationTime.getTime() - issueTime.getTime()) / 1000;
        assertThat(duration).isCloseTo(duration, within(5L));
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getClaim("scope")).isEqualTo("ROLE_USER ADD_FRIEND");
        assertThat(claims.getClaim("userId")).isEqualTo("123456789");

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, never()).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void outboundAuthenticate_userEmpty() throws Exception {
        ExchangeTokenResponse exchangeTokenResponse = ExchangeTokenResponse
                .builder()
                .accessToken("access_token")
                .build();

        OutboundUserResponse outboundUserResponse = OutboundUserResponse
                .builder()
                .id("123456789")
                .email("aireak@gmail.com")
                .verifiedEmail(true)
                .givenName("aireak")
                .familyName("Nguyen")
                .build();

        when(outboundIdentityClient.exchangeToken(any(ExchangeTokenRequest.class))).thenReturn(exchangeTokenResponse);
        when(outboundUserClient.getInfo("json", exchangeTokenResponse.getAccessToken())).thenReturn(outboundUserResponse);
        when(userRepository.findByEmail(outboundUserResponse.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);

        var response = authenticationService.outboundAuthenticate("123");

        String token = response.getToken();

        JWSObject jwsObject = JWSObject.parse(token);
        JWTClaimsSet claims = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

        assertThat(claims.getSubject()).isEqualTo("aireak@gmail.com");
        assertThat(claims.getIssuer()).isEqualTo("aireak.com");
        Date expirationTime = claims.getExpirationTime();
        Date issueTime = claims.getIssueTime();
        assertThat(expirationTime).isAfter(issueTime);
        long duration = (expirationTime.getTime() - issueTime.getTime()) / 1000;
        assertThat(duration).isCloseTo(duration, within(5L));
        assertThat(claims.getJWTID()).isNotNull();
        assertThat(claims.getClaim("scope")).isEqualTo("ROLE_USER ADD_FRIEND");
        assertThat(claims.getClaim("userId")).isEqualTo("123456789");

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, times(1)).findById(any());
        verify(passwordEncoder, times(1)).encode(any());
        verify(userRepository, times(1)).save(any());
        verify(outboxRepository, times(2)).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void outboundAuthenticate_roleNotExisted() {
        ExchangeTokenResponse exchangeTokenResponse = ExchangeTokenResponse
                .builder()
                .accessToken("access_token")
                .build();

        OutboundUserResponse outboundUserResponse = OutboundUserResponse
                .builder()
                .id("123456789")
                .email("aireak@gmail.com")
                .verifiedEmail(true)
                .givenName("aireak")
                .familyName("Nguyen")
                .build();

        when(outboundIdentityClient.exchangeToken(any(ExchangeTokenRequest.class))).thenReturn(exchangeTokenResponse);
        when(outboundUserClient.getInfo("json", exchangeTokenResponse.getAccessToken())).thenReturn(outboundUserResponse);
        when(userRepository.findByEmail(outboundUserResponse.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findById("USER")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class, () -> authenticationService.outboundAuthenticate("123"));

        assertEquals(ErrorCode.ROLE_NOT_EXISTED, exception.getErrorCode());

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, times(1)).findById(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void outboundAuthenticate_signerKeyInvalid() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        ExchangeTokenResponse exchangeTokenResponse = ExchangeTokenResponse
                .builder()
                .accessToken("access_token")
                .build();

        OutboundUserResponse outboundUserResponse = OutboundUserResponse
                .builder()
                .id("123456789")
                .email("aireak@gmail.com")
                .verifiedEmail(true)
                .givenName("aireak")
                .familyName("Nguyen")
                .build();

        when(outboundIdentityClient.exchangeToken(any(ExchangeTokenRequest.class))).thenReturn(exchangeTokenResponse);
        when(outboundUserClient.getInfo("json", exchangeTokenResponse.getAccessToken())).thenReturn(outboundUserResponse);
        when(userRepository.findByEmail(outboundUserResponse.getEmail())).thenReturn(Optional.empty());
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role));
        when(userRepository.save(any())).thenReturn(user);

        var exception = assertThrows(AppException.class, () -> authenticationService.outboundAuthenticate("123"));

        assertEquals(ErrorCode.SIGNER_EXCEPTION, exception.getErrorCode());

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, times(1)).findById(any());
        verify(passwordEncoder, times(1)).encode(any());
        verify(userRepository, times(1)).save(any());
        verify(outboxRepository, times(2)).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }
}

