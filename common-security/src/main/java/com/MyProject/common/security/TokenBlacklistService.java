package com.MyProject.common.security;

import com.MyProject.common.constant.TokenBlacklistKeys;
import com.MyProject.common.redis.RedisService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Immediate access-token revocation, backed by Redis so every service that verifies a JWT
 * (identity-service, api-gateway, and everything using CommonJwtDecoder) sees the same state.
 *
 * Two independent mechanisms, both keyed off the token's own claims - no server-side session
 * table to look up on every request:
 *  - single-token blacklist (jti): used for "log out this one session".
 *  - per-user cutoff (iat comparison): used for "log out every session" (password change,
 *    account deactivation) without having to enumerate every access token ever issued.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenBlacklistService {
    RedisService redisService;

    public void blacklistAccessToken(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) return;

        long ttlSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds <= 0) return;

        redisService.setWithExpiration(TokenBlacklistKeys.invalidatedTokenKey(jti), "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null) return false;
        return redisService.hasKey(TokenBlacklistKeys.invalidatedTokenKey(jti));
    }

    public void invalidateAllTokensForUser(String userId, long maxAccessTokenTtlSeconds) {
        if (userId == null || maxAccessTokenTtlSeconds <= 0) return;

        redisService.setWithExpiration(
                TokenBlacklistKeys.userTokensValidAfterKey(userId),
                String.valueOf(Instant.now().toEpochMilli()),
                maxAccessTokenTtlSeconds,
                TimeUnit.SECONDS);
    }

    public boolean isIssuedBeforeUserCutoff(String userId, Instant issueTime) {
        if (userId == null || issueTime == null) return false;

        String cutoffMillis = redisService.getAsString(TokenBlacklistKeys.userTokensValidAfterKey(userId));
        if (cutoffMillis == null) return false;

        return issueTime.toEpochMilli() <= Long.parseLong(cutoffMillis);
    }
}
