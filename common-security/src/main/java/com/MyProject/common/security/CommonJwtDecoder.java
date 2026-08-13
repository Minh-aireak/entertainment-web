package com.MyProject.common.security;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.Date;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommonJwtDecoder implements JwtDecoder {
    // Optional: several services @Import this class directly into narrow @WebMvcTest slices
    // without the Redis-backed security auto-configuration, so revocation must degrade rather
    // than blow up context loading when it's absent. Real app contexts always wire it (common-redis
    // is now a hard dependency of common-security), so production checks always run for real.
    @NonFinal
    @Autowired(required = false)
    TokenBlacklistService tokenBlacklistService;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            verifyToken(signedJWT);

            return new Jwt(token,
                    signedJWT.getJWTClaimsSet().getIssueTime().toInstant(),
                    signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                    signedJWT.getHeader().toJSONObject(),
                    signedJWT.getJWTClaimsSet().getClaims());
        } catch (ParseException | JOSEException e) {
            throw new JwtException(e.getMessage());
        }
    }

    private void verifyToken(SignedJWT signedJWT) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());

        if (!signedJWT.verify(verifier)) {
            throw new JwtException("Invalid signature");
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        Date expiryTime = claims.getExpirationTime();
        if (expiryTime.before(new Date())) {
            throw new JwtException("Token expired");
        }

        if (tokenBlacklistService == null) {
            return;
        }

        String jti = claims.getJWTID();
        if (tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
            throw new JwtException("Token has been revoked");
        }

        Object userIdClaim = claims.getClaim("userId");
        if (userIdClaim != null && claims.getIssueTime() != null
                && tokenBlacklistService.isIssuedBeforeUserCutoff(userIdClaim.toString(), claims.getIssueTime().toInstant())) {
            throw new JwtException("Token has been revoked");
        }
    }
}
