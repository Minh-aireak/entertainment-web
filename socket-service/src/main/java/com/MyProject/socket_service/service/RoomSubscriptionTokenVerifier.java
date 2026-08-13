package com.MyProject.socket_service.service;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/** Verifies room-service-issued watch-together subscription tokens (see room-service's
 *  RoomSubscriptionTokenService and RoomResponse.wsToken). Signed with the same shared
 *  "jwt.signerKey" secret CommonJwtDecoder already uses for access tokens across services - no
 *  new secret plumbing - but this is a purpose-built token (userId + roomId + short expiry),
 *  verified independently rather than through CommonJwtDecoder. */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSubscriptionTokenVerifier {
    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    /** Never throws - any failure (malformed, expired, bad signature, userId/roomId mismatch)
     *  just returns false so the caller can refuse the join rather than crash the socket. */
    public boolean verify(String token, String expectedUserId, String expectedRoomId) {
        if (!StringUtils.hasText(token)) return false;
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
            if (!signedJWT.verify(verifier)) return false;

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.before(new Date())) return false;

            Object userIdClaim = claims.getClaim("userId");
            Object roomIdClaim = claims.getClaim("roomId");
            return expectedUserId.equals(userIdClaim) && expectedRoomId.equals(roomIdClaim);
        } catch (Exception e) {
            log.warn("Failed to verify room subscription token", e);
            return false;
        }
    }
}
