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

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonJwtDecoderTest {

    private static final String SIGNER_KEY = "a".repeat(64);

    private CommonJwtDecoder commonJwtDecoder;

    @BeforeEach
    void setUp() {
        commonJwtDecoder = new CommonJwtDecoder();
        ReflectionTestUtils.setField(commonJwtDecoder, "signerKey", SIGNER_KEY);
    }

    private String signedToken(String signerKey, Date issueTime, Date expiryTime, String userId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
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
}
