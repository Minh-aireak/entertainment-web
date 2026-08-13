package com.MyProject.profile.profile_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.profile.profile_service.configuration.ProfileRateLimitProperties;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileApiRateLimitServiceTest {

    @Mock RedisService redisService;

    ProfileRateLimitProperties properties;
    ProfileApiRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        properties = new ProfileRateLimitProperties();
        properties.getProfileWrite().setLimitForPeriod(15);
        properties.getProfileWrite().setLimitRefreshPeriod(Duration.ofSeconds(3));
        rateLimitService = new ProfileApiRateLimitService(redisService, properties);
    }

    @Test
    void checkProfileWrite_underLimit_doesNotThrow() {
        when(redisService.tryAcquireRateLimit(eq("profile:rate-limit:profile-write:user-1"), eq(15L), eq(3L)))
                .thenReturn(true);

        assertThatCode(() -> rateLimitService.checkProfileWrite("user-1")).doesNotThrowAnyException();
    }

    @Test
    void checkProfileWrite_overLimit_throwsRateLimitExceeded() {
        when(redisService.tryAcquireRateLimit(eq("profile:rate-limit:profile-write:user-1"), eq(15L), eq(3L)))
                .thenReturn(false);

        assertThatThrownBy(() -> rateLimitService.checkProfileWrite("user-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void checkProfileRead_redisUnavailable_failsOpenInsteadOfBlocking() {
        when(redisService.tryAcquireRateLimit(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> rateLimitService.checkProfileRead("user-1")).doesNotThrowAnyException();
    }

    @Test
    void checkProfileSearch_usesSearchSpecificKey() {
        when(redisService.tryAcquireRateLimit(eq("profile:rate-limit:profile-search:user-1"), anyLong(), anyLong()))
                .thenReturn(true);

        rateLimitService.checkProfileSearch("user-1");

        verify(redisService).tryAcquireRateLimit(eq("profile:rate-limit:profile-search:user-1"), anyLong(), anyLong());
    }

    @Test
    void checkProfileSummary_usesSummarySpecificKey() {
        when(redisService.tryAcquireRateLimit(eq("profile:rate-limit:profile-summary:user-1"), anyLong(), anyLong()))
                .thenReturn(true);

        rateLimitService.checkProfileSummary("user-1");

        verify(redisService).tryAcquireRateLimit(eq("profile:rate-limit:profile-summary:user-1"), anyLong(), anyLong());
    }
}
