package com.MyProject.profile.profile_service.service;

import com.MyProject.profile.profile_service.configuration.ProfileRateLimitProperties;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "profile:rate-limit:";

    private final RedisService redisService;
    private final ProfileRateLimitProperties properties;

    public void checkProfileWrite(String userId) {
        enforce("profile-write:" + userId, properties.getProfileWrite());
    }

    public void checkProfileRead(String userId) {
        enforce("profile-read:" + userId, properties.getProfileRead());
    }

    public void checkProfileSearch(String userId) {
        enforce("profile-search:" + userId, properties.getProfileSearch());
    }

    public void checkProfileSummary(String userId) {
        enforce("profile-summary:" + userId, properties.getProfileSummary());
    }

    private void enforce(String keySuffix, ProfileRateLimitProperties.Rule rule) {
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
            // Fail-open so Redis issues do not block profile APIs entirely.
            log.warn("Skip custom rate limit check for key {} because Redis is unavailable.", keySuffix, exception);
        }
    }
}
