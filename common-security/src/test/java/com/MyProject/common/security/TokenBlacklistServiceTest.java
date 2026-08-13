package com.MyProject.common.security;

import com.MyProject.common.redis.RedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    RedisService redisService;

    TokenBlacklistService tokenBlacklistService;

    @Test
    void blacklistAccessToken_storesJtiWithRemainingTtl() {
        tokenBlacklistService = new TokenBlacklistService(redisService);
        Instant expiresAt = Instant.now().plusSeconds(120);

        tokenBlacklistService.blacklistAccessToken("jti-1", expiresAt);

        verify(redisService).setWithExpiration(
                eq("invalidated_token:jti-1"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void blacklistAccessToken_alreadyExpired_doesNothing() {
        tokenBlacklistService = new TokenBlacklistService(redisService);

        tokenBlacklistService.blacklistAccessToken("jti-1", Instant.now().minusSeconds(5));

        verify(redisService, never()).setWithExpiration(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void isAccessTokenBlacklisted_reflectsRedisState() {
        tokenBlacklistService = new TokenBlacklistService(redisService);
        when(redisService.hasKey("invalidated_token:jti-1")).thenReturn(true);

        assertThat(tokenBlacklistService.isAccessTokenBlacklisted("jti-1")).isTrue();
    }

    @Test
    void isIssuedBeforeUserCutoff_tokenOlderThanCutoff_isRevoked() {
        tokenBlacklistService = new TokenBlacklistService(redisService);
        Instant cutoff = Instant.now();
        when(redisService.getAsString("token_valid_after:user-1"))
                .thenReturn(String.valueOf(cutoff.toEpochMilli()));

        boolean revoked = tokenBlacklistService.isIssuedBeforeUserCutoff("user-1", cutoff.minusSeconds(1));

        assertThat(revoked).isTrue();
    }

    @Test
    void isIssuedBeforeUserCutoff_tokenNewerThanCutoff_isValid() {
        tokenBlacklistService = new TokenBlacklistService(redisService);
        Instant cutoff = Instant.now();
        when(redisService.getAsString("token_valid_after:user-1"))
                .thenReturn(String.valueOf(cutoff.toEpochMilli()));

        boolean revoked = tokenBlacklistService.isIssuedBeforeUserCutoff("user-1", cutoff.plusSeconds(1));

        assertThat(revoked).isFalse();
    }

    @Test
    void isIssuedBeforeUserCutoff_noCutoffStored_isValid() {
        tokenBlacklistService = new TokenBlacklistService(redisService);
        when(redisService.getAsString("token_valid_after:user-1")).thenReturn(null);

        boolean revoked = tokenBlacklistService.isIssuedBeforeUserCutoff("user-1", Instant.now());

        assertThat(revoked).isFalse();
    }
}
