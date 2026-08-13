package com.MyProject.room_service.service;

import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** Mints a short-lived signed token proving a caller may subscribe to a watch-together room's
 *  WebSocket channel (see RoomController's RoomResponse.wsToken and socket-service's
 *  CustomWebSocketHandler join-room handling). Signed with the same "jwt.signerKey" secret
 *  CommonJwtDecoder already uses for access tokens - no new shared-secret plumbing needed -
 *  but this is a purpose-built token (userId + roomId + short expiry only), not an access token,
 *  so it is verified independently rather than through CommonJwtDecoder. */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSubscriptionTokenService {
    private static final long TOKEN_TTL_SECONDS = 300;

    @NonFinal
    @Value("${jwt.signerKey}")
    String signerKey;

    public String issueToken(String userId, String roomId) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("aireak.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(TOKEN_TTL_SECONDS, ChronoUnit.SECONDS).toEpochMilli()))
                .claim("userId", userId)
                .claim("roomId", roomId)
                .build();

        JWSObject jwsObject = new JWSObject(header, new Payload(claims.toJSONObject()));
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
        } catch (Exception e) {
            throw new AppException(ErrorCode.TOKEN_SIGNING_FAILED);
        }

        return jwsObject.serialize();
    }
}
