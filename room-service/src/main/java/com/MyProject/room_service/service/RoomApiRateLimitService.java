package com.MyProject.room_service.service;

import com.MyProject.room_service.configuration.RoomRateLimitProperties;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "room:rate-limit:";

    private final RedisService redisService;
    private final RoomRateLimitProperties properties;

    public void checkRoomCreate(String userId) {
        enforce("room-create:" + userId, properties.getRoomCreate());
    }

    public void checkRoomRead(String userId) {
        enforce("room-read:" + userId, properties.getRoomRead());
    }

    public void checkRoomPlayback(String userId, String roomId) {
        enforce("room-playback:" + userId + ":" + roomId, properties.getRoomPlayback());
    }

    public void checkRoomMessage(String userId, String roomId) {
        enforce("room-message:" + userId + ":" + roomId, properties.getRoomMessage());
    }

    private void enforce(String keySuffix, RoomRateLimitProperties.Rule rule) {
        try {
            long windowSeconds = Math.max(1, rule.getLimitRefreshPeriod().toSeconds());
            boolean allowed = redisService.tryAcquireRateLimit(
                    RATE_LIMIT_PREFIX + keySuffix,
                    rule.getLimitForPeriod(),
                    windowSeconds
            );
            if (!allowed) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Skip custom rate limit check for key {} because Redis is unavailable.", keySuffix, exception);
        }
    }
}
