package com.MyProject.identity.identity_service.service;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.request.IntrospectRequest;
import com.MyProject.identity.identity_service.dto.request.LogoutRequest;
import com.MyProject.identity.identity_service.dto.request.RefreshRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.entity.Permission;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.repository.InvalidatedTokenRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource("/test.properties")
class AuthenticationServiceTest {
    @Autowired
    AuthenticationService authenticationService;

    @MockitoBean
    InvalidatedTokenRepository invalidatedTokenRepository;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    User user;
    AuthenticationRequest request;
    AuthenticationResponse response;
    LogoutRequest logoutRequest;
    AuthenticationService spyService;
    Jwt jwt;
    IntrospectRequest introspectRequest;
    IntrospectResponse introspectResponse;
    RefreshRequest refreshRequest;
    final String signerKey = "uahkQQArjCd/s458SRMXBYTBbV8FiePF7TPREMKeRijTgiD6DOkEdtmxtcGPVGP0";
    final String expectedJid = "EXPECTED_JID";
    String validToken;
    SecretKey secretKey;
    long validDuration;
    long refreshableDuration;
    Role role;
    Permission permission;
    SignedJWT signedJWT;
    JWTClaimsSet jwtClaimsSet;

    @BeforeEach
    void initData() throws JOSEException {
        signedJWT = mock(SignedJWT.class);
        jwtClaimsSet = mock(JWTClaimsSet.class);

        secretKey = Keys.hmacShaKeyFor(signerKey.getBytes());
        validToken = Jwts.builder()
                .setId(expectedJid)
                .setIssuedAt(new Date())
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
        validDuration = 3600;
        refreshableDuration = 7200;

        AuthenticationService realService = AopTestUtils.getTargetObject(authenticationService);
        spyService = spy(realService);

        permission = Permission.builder()
                .name("READ")
                .build();

        role = Role.builder()
                .name("USER")
                .permissions(Set.of(permission))
                .build();

        user = User.builder()
                .username("NguyenTuanMinh")
                .password("decoded!")
                .lastName("Minh")
                .roles(Set.of(role))
                .build();

        request = AuthenticationRequest.builder()
                .username("NguyenTuanMinh")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .build();

        response = AuthenticationResponse.builder()
                .token("123456789")
                .build();

        logoutRequest = LogoutRequest.builder()
                .token("123456789")
                .build();

        jwt = mock(Jwt.class);
        when(jwt.getId()).thenReturn("REDACTED_LEGACY_CREDENTIAL");

        introspectRequest = IntrospectRequest.builder()
                .token("123456789")
                .build();

        refreshRequest = RefreshRequest.builder()
                .token("123456789")
                .build();
    }

    @Test
    void buildScope_success(){
        String scope = authenticationService.buildScope(user);

        Assertions.assertThat(scope)
                .contains("ROLE_USER")
                .contains("READ")
                .contains(" ")
                .doesNotContain(",")
                .doesNotContain(".");
    }

    @Test
    void buildScope_roleEmpty(){
        user.setRoles(null);

        String scope = authenticationService.buildScope(user);

        assertEquals(scope, "");
    }

    @Test
    void buildScope_roleNotEmpty_permissionEmpty(){
        role.setPermissions(null);

        String scope = authenticationService.buildScope(user);

        assertEquals(scope, "ROLE_USER");
    }

    @Test
    void generateToken_success() throws JOSEException, ParseException {
        Instant fixedTime = Instant.parse("2023-01-01T00:00:00Z");
        try (MockedStatic<Instant> mockedStatic = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStatic.when(Instant::now).thenReturn(fixedTime);
            doReturn("ROLE_USER READ").when(spyService).buildScope(user);

            String token = spyService.generateToken(user);

            assertNotNull(token);

            JWSObject jwsObject = JWSObject.parse(token);
            JWTClaimsSet jwtClaimsSet = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());

            assertEquals("NguyenTuanMinh", jwtClaimsSet.getSubject());
            assertEquals("aireak.com", jwtClaimsSet.getIssuer());
            assertEquals(fixedTime.plus(validDuration, ChronoUnit.SECONDS).toEpochMilli()
                    , jwtClaimsSet.getExpirationTime().getTime());
            assertEquals("ROLE_USER READ", jwtClaimsSet.getClaim("scope").toString());
        }
    }

    @Test
    void authentication_login_success() throws JOSEException {
        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        doReturn(response.getToken()).when(spyService).generateToken(user);

        var actual = spyService.authentication(request);

        verify(userRepository).findByUsername(any());
        verify(passwordEncoder, times(1)).matches(any(), any());
        verify(spyService).generateToken(any());

        assertNotNull(actual);
        Assertions.assertThat(actual.getToken()).isEqualTo("123456789");
    }

    @Test
    void authentication_userNotExisted_return1002(){
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class,
                () -> authenticationService.authentication(request));

        verify(userRepository).findByUsername(any());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("User not existed!");
    }

    @Test
    void authentication_wrongPassword_return1011(){
        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        var exception = assertThrows(AppException.class,
                () -> authenticationService.authentication(request));

        verify(userRepository).findByUsername(any());
        verify(passwordEncoder).matches(any(), any());

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1011);
        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Password incorrect!");
    }

    @Test
    void checkJidAndSignerKey_success_returnTrue(){
        boolean result = authenticationService.checkJidAndSignerKey(validToken, expectedJid);

        assertTrue(result);
    }

    @Test
    void checkJidAndSignerKey_invalidToken_returnFalse(){
        validToken = Jwts.builder()
                .setId("gbjWtBF9h0hhdoKBiZrDTA")
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        boolean result = authenticationService.checkJidAndSignerKey(validToken, expectedJid);

        assertFalse(result);
    }

    @Test
    void checkJidAndSignerKey_throwsWeakKeyException_return1021(){
        validToken = Jwts.builder()
                .setId("EXPECTED_JID")
                .signWith(Keys.hmacShaKeyFor(signerKey.getBytes()))
                .compact();

        ReflectionTestUtils.setField(authenticationService, "signerKey", "1");

        var exception = assertThrows(AppException.class, () -> authenticationService.checkJidAndSignerKey(validToken, expectedJid));

        assertEquals(exception.getErrorCode().getCode(), 1021);
        assertEquals(exception.getErrorCode().getMessage(), "Key length is weak!");
    }

    @Test
    void whenUsingWeakKey_throwWeakKeyException() {
        String shortKey = "123";

        assertThrows(WeakKeyException.class, () -> {
            Keys.hmacShaKeyFor(shortKey.getBytes());
        });
    }

    @Test
    void checkJidAndSignerKey_invalidJid_returnFalse(){
        boolean result = authenticationService.checkJidAndSignerKey(validToken, "12345");
        assertFalse(result);
    }

    @Test
    void verifyToken_success_booleanIsTrue() throws JOSEException, ParseException {
        try (MockedStatic<SignedJWT> mockedStatic = mockStatic(SignedJWT.class)){
            String token = "valid.access.token";

            mockedStatic.when(() -> SignedJWT.parse(token)).thenReturn(signedJWT);
            when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
            when(jwtClaimsSet.getIssueTime())
                    .thenReturn(Date.from(Instant.now().plusSeconds(refreshableDuration)));
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);
            when(signedJWT.verify(any())).thenReturn(true);

            var result = authenticationService.verifyToken(token, true);

            assertNotNull(result);
        }
    }

    @Test
    void verifyToken_success_booleanIsFalse() throws JOSEException, ParseException {
        try (MockedStatic<SignedJWT> mockedStatic = mockStatic(SignedJWT.class)){
            String token = "valid.access.token";

            mockedStatic.when(() -> SignedJWT.parse(token)).thenReturn(signedJWT);
            when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
            when(jwtClaimsSet.getExpirationTime())
                    .thenReturn(Date.from(Instant.now().plusSeconds(validDuration)));
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);
            when(signedJWT.verify(any())).thenReturn(true);

            var result = authenticationService.verifyToken(token, false);

            assertNotNull(result);
        }
    }

    @Test
    void verifyToken_invalidSignature_throwsTokenInvalid() throws Exception{
        try (MockedStatic<SignedJWT> mockedStatic = mockStatic(SignedJWT.class)){
            String token = "valid.access.token";

            mockedStatic.when(() -> SignedJWT.parse(token)).thenReturn(signedJWT);
            when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
            when(jwtClaimsSet.getExpirationTime())
                    .thenReturn(Date.from(Instant.now().plusSeconds(validDuration)));
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);
            when(signedJWT.verify(any())).thenReturn(false);

            var exception = assertThrows(AppException.class
                    , () -> authenticationService.verifyToken(token, false));

            assertEquals(exception.getErrorCode().getCode(), 1015);
            assertEquals(exception.getErrorCode().getMessage(), "Token invalid!");
        }
    }

    @Test
    void verifyToken_expiredAccessToken_throwsTokenInvalid() throws Exception {
        try (MockedStatic<SignedJWT> mockedSignedJWT = mockStatic(SignedJWT.class)) {
            String token = "expired.access.token";

            mockedSignedJWT.when(() -> SignedJWT.parse(token)).thenReturn(signedJWT);
            when(signedJWT.verify(any())).thenReturn(true);
            when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
            when(jwtClaimsSet.getExpirationTime())
                    .thenReturn(Date.from(Instant.now()));

            var exception = assertThrows(AppException.class, () ->
                            authenticationService.verifyToken(token, false));

            assertEquals(exception.getErrorCode().getCode(), 1015);
            assertEquals(exception.getErrorCode().getMessage(), "Token invalid!");
        }
    }

    @Test
    void verifyToken_tokenAlreadyInvalidated_throwsAppException() throws Exception{
        try (MockedStatic<SignedJWT> mockedSignedJWT = mockStatic(SignedJWT.class)) {
            String token = "invalid.token.success";

            mockedSignedJWT.when(() -> SignedJWT.parse(token)).thenReturn(signedJWT);
            when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
            when(jwtClaimsSet.getExpirationTime())
                    .thenReturn(Date.from(Instant.now().plusSeconds(validDuration)));
            when(signedJWT.verify(any())).thenReturn(true);
            when(invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
                    .thenReturn(true);

            var exception = assertThrows(AppException.class, () ->
                    authenticationService.verifyToken(token, false));

            assertEquals(exception.getErrorCode().getCode(), 1016);
            assertEquals(exception.getErrorCode().getMessage(), "Token already invalidated!");
        }
    }

    @Test
    void logout_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(AuthenticationService.class.getMethod("logout", LogoutRequest.class, Jwt.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void logout_success() throws Exception {
        doReturn(true).when(spyService).checkJidAndSignerKey(any(), any());
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
        when(jwtClaimsSet.getJWTID()).thenReturn("REDACTED_LEGACY_CREDENTIAL");
        when(jwtClaimsSet.getExpirationTime()).thenReturn(Date.from(Instant.now()));

        spyService.logout(logoutRequest, jwt);

        verify(invalidatedTokenRepository, times(1)).save(any());
    }

    @Test
    void logout_accessDenied_return1022(){
        doReturn(false).when(spyService).checkJidAndSignerKey(any(), any());
        var exception = assertThrows(AppException.class
                , () -> spyService.logout(logoutRequest, jwt));

        verify(invalidatedTokenRepository, never()).save(any());

        assertEquals(exception.getErrorCode().getCode(), 1022);
        assertEquals(exception.getErrorCode().getMessage(), "Token not owned by user!");
    }

    @Test
    void introspect_returnTrue() throws Exception {
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        IntrospectResponse result = spyService.introspectResponse(introspectRequest);

        assertTrue(result.isValid());
    }

    @Test
    void introspect_JOSEException_returnFalse() throws Exception {
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        doThrow(new JOSEException("JOSEException!")).when(spyService).verifyToken(anyString(), anyBoolean());

        IntrospectResponse result = spyService.introspectResponse(introspectRequest);

        assertFalse(result.isValid());
    }

    @Test
    void introspect_ParseException_returnFalse() throws Exception {
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        doThrow(new ParseException("ParseException!", 0)).when(spyService).verifyToken(anyString(), anyBoolean());

        IntrospectResponse result = spyService.introspectResponse(introspectRequest);

        assertFalse(result.isValid());
    }

    @Test
    void refreshToken_shouldHaveTransactionalAnnotation() throws Exception{
        Assertions.assertThat(AuthenticationService.class.getMethod("refreshToken", RefreshRequest.class, Jwt.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void refreshToken_success() throws Exception{
        doReturn(true).when(spyService).checkJidAndSignerKey(anyString(), anyString());
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
        when(jwtClaimsSet.getJWTID()).thenReturn("123456789");
        when(jwtClaimsSet.getExpirationTime())
                .thenReturn(Date.from(Instant.now()));
        when(jwtClaimsSet.getSubject())
                .thenReturn("NguyenTuanMinh");
        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.of(user));
        doReturn("123456789").when(spyService).generateToken(user);

        var result = spyService.refreshToken(refreshRequest, jwt);

        verify(invalidatedTokenRepository, times(1)).save(any());
        verify(userRepository, times(1)).findByUsername(any());

        assertEquals(result.getToken(), response.getToken());
    }

    @Test
    void refreshToken_accessDenied_return1022(){
        doReturn(false).when(spyService).checkJidAndSignerKey(any(), any());
        var exception = assertThrows(AppException.class
                , () -> spyService.refreshToken(refreshRequest, jwt));

        verify(invalidatedTokenRepository, never()).save(any());

        assertEquals(exception.getErrorCode().getCode(), 1022);
        assertEquals(exception.getErrorCode().getMessage(), "Token not owned by user!");
    }

    @Test
    void refreshToken_userNotExisted_return1002() throws Exception {
        doReturn(true).when(spyService).checkJidAndSignerKey(anyString(), anyString());
        doReturn(signedJWT).when(spyService).verifyToken(anyString(), anyBoolean());
        when(signedJWT.getJWTClaimsSet()).thenReturn(jwtClaimsSet);
        when(jwtClaimsSet.getJWTID()).thenReturn("123456789");
        when(jwtClaimsSet.getExpirationTime())
                .thenReturn(Date.from(Instant.now()));
        when(jwtClaimsSet.getSubject())
                .thenReturn("NguyenTuanMinh");
        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.empty());

        var exception = assertThrows(AppException.class
                , () -> spyService.refreshToken(refreshRequest, jwt));

        verify(invalidatedTokenRepository, times(1)).save(any());
        verify(userRepository, times(1)).findByUsername(any());

        assertEquals(exception.getErrorCode().getCode(), 1002);
        assertEquals(exception.getErrorCode().getMessage(), "User not existed!");
    }
}
