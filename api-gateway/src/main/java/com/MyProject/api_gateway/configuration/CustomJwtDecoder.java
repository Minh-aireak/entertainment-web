package com.MyProject.api_gateway.configuration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.Date;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomJwtDecoder implements ReactiveJwtDecoder {
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

            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiryTime.before(new Date())) {
                return Mono.error(new JwtException("Token expired"));
            }

            String jid = signedJWT.getJWTClaimsSet().getJWTID();
            
            // Skip Redis check as requested
            return Mono.empty();
        } catch (JOSEException | ParseException e) {
            return Mono.error(new JwtException(e.getMessage()));
        }
    }
}
