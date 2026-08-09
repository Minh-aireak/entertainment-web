package com.MyProject.room_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.room_service.configuration.RoomRateLimitProperties;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomApiRateLimitServiceTest {

    @Mock RedisService redisService;

    RoomRateLimitProperties properties;
    RoomApiRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        properties = new RoomRateLimitProperties();
        properties.getRoomCreate().setLimitForPeriod(5);
        properties.getRoomCreate().setLimitRefreshPeriod(Duration.ofSeconds(60));
        rateLimitService = new RoomApiRateLimitService(redisService, properties);
    }

    @Test
    void checkRoomCreate_underLimit_doesNotThrow() {
        when(redisService.tryAcquireRateLimit(eq("room:rate-limit:room-create:user-1"), eq(5L), eq(60L))).thenReturn(true);

        assertThatCode(() -> rateLimitService.checkRoomCreate("user-1")).doesNotThrowAnyException();
    }

    @Test
    void checkRoomCreate_overLimit_throwsRateLimitExceeded() {
        when(redisService.tryAcquireRateLimit(eq("room:rate-limit:room-create:user-1"), eq(5L), eq(60L))).thenReturn(false);

        assertThatThrownBy(() -> rateLimitService.checkRoomCreate("user-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void checkRoomPlayback_redisUnavailable_failsOpenInsteadOfBlockingPlayback() {
        when(redisService.tryAcquireRateLimit(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> rateLimitService.checkRoomPlayback("user-1", "room-1")).doesNotThrowAnyException();
    }

    @Test
    void checkRoomMessage_usesCompositeKeyOfUserAndRoom() {
        when(redisService.tryAcquireRateLimit(eq("room:rate-limit:room-message:user-1:room-1"), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(true);

        rateLimitService.checkRoomMessage("user-1", "room-1");

        verify(redisService).tryAcquireRateLimit(eq("room:rate-limit:room-message:user-1:room-1"), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
