package com.MyProject.identity.identity_service.service;

import com.MyProject.common_dto.event.dto.IntrospectRequest;
import com.MyProject.common_dto.event.dto.IntrospectResponse;
import com.MyProject.identity.identity_service.dto.request.ExchangeTokenRequest;
import com.MyProject.identity.identity_service.dto.response.ExchangeTokenResponse;
import com.MyProject.identity.identity_service.dto.response.OutboundUserResponse;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;
import com.MyProject.identity.identity_service.repository.httpclient.UserProfileClient;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.request.LogoutRequest;
import com.MyProject.identity.identity_service.dto.request.RefreshRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationServiceTest {
    @Autowired
    AuthenticationService authenticationService;

    @MockitoBean
    InvalidatedTokenRepository invalidatedTokenRepository;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    @MockitoBean
    OutboundIdentityClient outboundIdentityClient;

    @MockitoBean
    OutboundUserClient outboundUserClient;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    UserProfileClient client;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    User user;
    AuthenticationRequest authenticationRequest;
    Role role;

    @BeforeEach
    void initData() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", signerKey);
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
        long duration = (expirationTime.getTime() - issueTime.getTime()) / 1000;
        assertThat(duration).isCloseTo(duration, within(5L));
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
        when(passwordEncoder.matches(authenticationRequest.getUsername(), user.getPassword())).thenReturn(false);

        var exception = assertThrows(AppException.class, () -> authenticationService.authentication(authenticationRequest));

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
        long duration = (expirationTime.getTime() - issueTime.getTime()) / 1000;
        assertThat(duration).isCloseTo(duration, within(5L));
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
        long duration = (expirationTime.getTime() - issueTime.getTime()) / 1000;
        assertThat(duration).isCloseTo(duration, within(5L));
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
        LogoutRequest request = new LogoutRequest(logoutToken);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);

        authenticationService.logout(request);

        verify(invalidatedTokenRepository, times(1)).existsById(any());
        verify(invalidatedTokenRepository, times(1)).save(any());
    }

    @Test
    void logout_unAuthenticated() throws Exception {
        String logoutToken = createValidAccessToken("123456789");
        LogoutRequest request = new LogoutRequest(logoutToken);
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(authentication.getPrincipal()).thenReturn(null);

        var exception = assertThrows(AppException.class, () -> authenticationService.logout(request));

        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void logoutToken_accessDenied() throws Exception {
        mockAuthenticatedUser();
        String logoutToken = createValidAccessToken("12345678910");
        LogoutRequest request = new LogoutRequest(logoutToken);

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
        LogoutRequest request = new LogoutRequest(logoutToken);

        var exception = assertThrows(AppException.class, () -> authenticationService.logout(request));

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());

        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(invalidatedTokenRepository, never()).save(any());
    }

    @Test
    void introspect_success() throws Exception {
        String token = createValidAccessToken("123456789");
        IntrospectRequest request = new IntrospectRequest(token);

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(false);

        IntrospectResponse response = authenticationService.introspectResponse(request);

        assertTrue(response.isValid());
        assertEquals("123456789", response.getUserId());
    }

    @Test
    void introspect_expiredToken_tokenInvalid() throws Exception {
        mockAuthenticatedUser();
        String expiredToken = createExpiredRefreshToken();
        IntrospectRequest introspectRequest = IntrospectRequest.builder()
                .token(expiredToken)
                .build();

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(introspectRequest));

        verify(invalidatedTokenRepository, never()).existsById(any());

        assertEquals(ErrorCode.TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void introspect_alreadyInvalidatedToken() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        IntrospectRequest introspectRequest = IntrospectRequest.builder()
                .token(refreshToken)
                .build();

        when(invalidatedTokenRepository.existsById(anyString())).thenReturn(true);

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(introspectRequest));

        assertEquals(ErrorCode.TOKEN_ALREADY_INVALIDATED, exception.getErrorCode());

        verify(invalidatedTokenRepository, times(1)).existsById(any());
    }

    @Test
    void introspect_verifyTokenFailed() throws Exception {
        mockAuthenticatedUser();
        ReflectionTestUtils.setField(authenticationService, "signerKey", "");
        String token = createValidRefreshToken("123456789");
        IntrospectRequest introspectRequest = IntrospectRequest.builder()
                .token(token)
                .build();

        var exception = assertThrows(AppException.class, () -> authenticationService.introspectResponse(introspectRequest));

        assertEquals(ErrorCode.VERIFY_TOKEN_FAILED, exception.getErrorCode());

        verify(invalidatedTokenRepository, never()).existsById(any());
    }

    @Test
    void refreshToken_success() throws Exception {
        mockAuthenticatedUser();
        String refreshToken = createValidRefreshToken("123456789");
        RefreshRequest request = new RefreshRequest(refreshToken);

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
        String refreshToken = createValidRefreshToken("123456789");
        RefreshRequest request = new RefreshRequest(refreshToken);
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(authentication.getPrincipal()).thenReturn(null);

        var exception = assertThrows(AppException.class, () -> authenticationService.refreshToken(request));

        verify(invalidatedTokenRepository, never()).save(any());
        verify(invalidatedTokenRepository, never()).existsById(any());
        verify(userRepository, never()).findByUsername(any());

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void refreshToken_accessDenied() throws Exception {
        String token = createValidRefreshToken("12345678910");
        mockAuthenticatedUser();
        RefreshRequest request = new RefreshRequest(token);

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
        RefreshRequest request = new RefreshRequest(token);

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
        RefreshRequest request = new RefreshRequest(expiredToken);

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
        RefreshRequest request = new RefreshRequest(refreshToken);

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
        RefreshRequest request = new RefreshRequest(refreshToken);

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
        verify(client, never()).createProfile(any());
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
        assertThat(claims.getClaim("userId")).isEqualTo("EM_123456789");

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, times(1)).findById(any());
        verify(client, times(1)).createProfile(any());
        verify(passwordEncoder, times(1)).encode(any());
        verify(userRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(any(), any());
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
        verify(client, never()).createProfile(any());
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

        var exception = assertThrows(AppException.class, () -> authenticationService.outboundAuthenticate("123"));

        assertEquals(ErrorCode.SIGNER_EXCEPTION, exception.getErrorCode());

        verify(outboundIdentityClient, times(1)).exchangeToken(any());
        verify(outboundUserClient, times(1)).getInfo(any(), any());
        verify(userRepository, times(1)).findByEmail(any());
        verify(roleRepository, times(1)).findById(any());
        verify(client, times(1)).createProfile(any());
        verify(passwordEncoder, times(1)).encode(any());
        verify(userRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(any(), any());
    }
}

