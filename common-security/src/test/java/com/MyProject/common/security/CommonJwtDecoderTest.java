package com.MyProject.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonJwtDecoderTest {

    private static final String SIGNER_KEY = "a".repeat(64);

    private CommonJwtDecoder commonJwtDecoder;
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = mock(TokenBlacklistService.class);
        commonJwtDecoder = new CommonJwtDecoder();
        ReflectionTestUtils.setField(commonJwtDecoder, "signerKey", SIGNER_KEY);
        ReflectionTestUtils.setField(commonJwtDecoder, "tokenBlacklistService", tokenBlacklistService);
    }

    private String signedToken(String signerKey, Date issueTime, Date expiryTime, String userId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .jwtID("jti-" + System.nanoTime())
                .claim("userId", userId)
                .issueTime(issueTime)
                .expirationTime(expiryTime)
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        signedJWT.sign(new MACSigner(signerKey.getBytes()));
        return signedJWT.serialize();
    }

    @Test
    void decode_validToken_returnsJwtWithClaims() throws Exception {
        Date issueTime = new Date();
        Date expiryTime = new Date(System.currentTimeMillis() + 60_000);
        String token = signedToken(SIGNER_KEY, issueTime, expiryTime, "user-123");

        Jwt jwt = commonJwtDecoder.decode(token);

        assertThat(jwt.getClaim("userId").toString()).isEqualTo("user-123");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
    }

    @Test
    void decode_wrongSigningKey_throwsInvalidSignature() throws Exception {
        Date issueTime = new Date();
        Date expiryTime = new Date(System.currentTimeMillis() + 60_000);
        String token = signedToken("b".repeat(64), issueTime, expiryTime, "user-123");

        assertThatThrownBy(() -> commonJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void decode_expiredToken_throwsTokenExpired() throws Exception {
        Date issueTime = new Date(System.currentTimeMillis() - 120_000);
        Date expiryTime = new Date(System.currentTimeMillis() - 60_000);
        String token = signedToken(SIGNER_KEY, issueTime, expiryTime, "user-123");

        assertThatThrownBy(() -> commonJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Token expired");
    }

    @Test
    void decode_malformedToken_throwsJwtException() {
        assertThatThrownBy(() -> commonJwtDecoder.decode("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decode_blacklistedJti_throwsTokenRevoked() throws Exception {
        Date issueTime = new Date();
        Date expiryTime = new Date(System.currentTimeMillis() + 60_000);
        String token = signedToken(SIGNER_KEY, issueTime, expiryTime, "user-123");
        when(tokenBlacklistService.isAccessTokenBlacklisted(anyString())).thenReturn(true);

        assertThatThrownBy(() -> commonJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void decode_issuedBeforeUserCutoff_throwsTokenRevoked() throws Exception {
        Date issueTime = new Date();
        Date expiryTime = new Date(System.currentTimeMillis() + 60_000);
        String token = signedToken(SIGNER_KEY, issueTime, expiryTime, "user-123");
        when(tokenBlacklistService.isIssuedBeforeUserCutoff(anyString(), any(Instant.class))).thenReturn(true);

        assertThatThrownBy(() -> commonJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void decode_blacklistServiceNotWired_stillDecodesValidToken() throws Exception {
        // Mirrors how downstream services @Import this class directly into narrow @WebMvcTest
        // slices without the Redis-backed auto-configuration - must degrade, not blow up.
        ReflectionTestUtils.setField(commonJwtDecoder, "tokenBlacklistService", null);
        Date issueTime = new Date();
        Date expiryTime = new Date(System.currentTimeMillis() + 60_000);
        String token = signedToken(SIGNER_KEY, issueTime, expiryTime, "user-123");

        Jwt jwt = commonJwtDecoder.decode(token);

        assertThat(jwt.getClaim("userId").toString()).isEqualTo("user-123");
    }
}
