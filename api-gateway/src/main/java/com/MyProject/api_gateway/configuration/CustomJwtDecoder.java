package com.MyProject.api_gateway.configuration;

import com.MyProject.common.constant.TokenBlacklistKeys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomJwtDecoder implements ReactiveJwtDecoder {
    ReactiveStringRedisTemplate redisTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            return verifyToken(signedJWT)
                    .then(Mono.just(new Jwt(token,
                            signedJWT.getJWTClaimsSet().getIssueTime().toInstant(),
                            signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                            signedJWT.getHeader().toJSONObject(),
                            signedJWT.getJWTClaimsSet().getClaims())));
        } catch (ParseException e) {
            return Mono.error(new JwtException(e.getMessage()));
        }
    }

    private Mono<Void> verifyToken(SignedJWT signedJWT) {
        try {
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

            if (!signedJWT.verify(verifier)) {
                return Mono.error(new JwtException("Invalid signature"));
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date expiryTime = claims.getExpirationTime();
            if (expiryTime.before(new Date())) {
                return Mono.error(new JwtException("Token expired"));
            }

            String jti = claims.getJWTID();
            Object userIdClaim = claims.getClaim("userId");
            Instant issueTime = claims.getIssueTime() != null ? claims.getIssueTime().toInstant() : null;

            return redisTemplate.hasKey(TokenBlacklistKeys.invalidatedTokenKey(jti))
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return Mono.error(new JwtException("Token has been revoked"));
                        }
                        return checkUserCutoff(userIdClaim, issueTime);
                    });
        } catch (JOSEException | ParseException e) {
            return Mono.error(new JwtException(e.getMessage()));
        }
    }

    private Mono<Void> checkUserCutoff(Object userIdClaim, Instant issueTime) {
        if (userIdClaim == null || issueTime == null) {
            return Mono.empty();
        }

        return redisTemplate.opsForValue()
                .get(TokenBlacklistKeys.userTokensValidAfterKey(userIdClaim.toString()))
                .flatMap(cutoffMillis -> {
                    if (issueTime.toEpochMilli() <= Long.parseLong(cutoffMillis)) {
                        return Mono.<Void>error(new JwtException("Token has been revoked"));
                    }
                    return Mono.<Void>empty();
                });
    }
}
