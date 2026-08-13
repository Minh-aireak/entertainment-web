package com.MyProject.api_gateway.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomJwtDecoderTest {

    private static final String SIGNER_KEY = "a".repeat(64);

    private CustomJwtDecoder customJwtDecoder;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOperations;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        customJwtDecoder = new CustomJwtDecoder(redisTemplate);
        ReflectionTestUtils.setField(customJwtDecoder, "signerKey", SIGNER_KEY);
    }

    private String signedToken(String signerKey, Date issueTime, Date expiryTime) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .jwtID("jti-" + System.nanoTime())
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

    @Test
    void decode_blacklistedJti_errorsWithTokenRevoked() throws Exception {
        String token = signedToken(SIGNER_KEY, new Date(), new Date(System.currentTimeMillis() + 60_000));
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        Mono<Jwt> mono = customJwtDecoder.decode(token);

        assertThatThrownBy(mono::block)
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void decode_issuedBeforeUserCutoff_errorsWithTokenRevoked() throws Exception {
        Date issueTime = new Date();
        String token = signedToken(SIGNER_KEY, issueTime, new Date(System.currentTimeMillis() + 60_000));
        long cutoffAfterIssueTime = issueTime.getTime() + 1_000;
        when(valueOperations.get(anyString())).thenReturn(Mono.just(String.valueOf(cutoffAfterIssueTime)));

        Mono<Jwt> mono = customJwtDecoder.decode(token);

        assertThatThrownBy(mono::block)
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }
}
