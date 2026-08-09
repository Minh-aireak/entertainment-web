package com.MyProject.api_gateway.configuration;

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
import reactor.core.publisher.Mono;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomJwtDecoderTest {

    private static final String SIGNER_KEY = "a".repeat(64);

    private CustomJwtDecoder customJwtDecoder;

    @BeforeEach
    void setUp() {
        customJwtDecoder = new CustomJwtDecoder();
        ReflectionTestUtils.setField(customJwtDecoder, "signerKey", SIGNER_KEY);
    }

    private String signedToken(String signerKey, Date issueTime, Date expiryTime) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("userId", "user-123")
                .issueTime(issueTime)
                .expirationTime(expiryTime)
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        signedJWT.sign(new MACSigner(signerKey.getBytes()));
        return signedJWT.serialize();
    }

    @Test
    void decode_validToken_emitsJwtWithClaims() throws Exception {
        String token = signedToken(SIGNER_KEY, new Date(), new Date(System.currentTimeMillis() + 60_000));

        Jwt jwt = customJwtDecoder.decode(token).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaim("userId").toString()).isEqualTo("user-123");
    }

    @Test
    void decode_wrongSigningKey_errorsWithInvalidSignature() throws Exception {
        String token = signedToken("b".repeat(64), new Date(), new Date(System.currentTimeMillis() + 60_000));

        Mono<Jwt> mono = customJwtDecoder.decode(token);

        assertThatThrownBy(mono::block)
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void decode_expiredToken_errorsWithTokenExpired() throws Exception {
        String token = signedToken(SIGNER_KEY,
                new Date(System.currentTimeMillis() - 120_000),
                new Date(System.currentTimeMillis() - 60_000));

        Mono<Jwt> mono = customJwtDecoder.decode(token);

        assertThatThrownBy(mono::block)
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Token expired");
    }

    @Test
    void decode_malformedToken_errorsWithJwtException() {
        Mono<Jwt> mono = customJwtDecoder.decode("not-a-jwt");

        assertThatThrownBy(mono::block)
                .isInstanceOf(JwtException.class);
    }
}
