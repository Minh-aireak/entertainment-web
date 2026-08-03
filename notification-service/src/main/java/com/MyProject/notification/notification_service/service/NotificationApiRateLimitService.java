
package com.MyProject.notification.notification_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.configuration.NotificationRateLimitProperties;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "notification:rate-limit:";

    private final RedisService redisService;
    private final NotificationRateLimitProperties properties;

    public void checkGetMyNotifications(String userId) {
        enforce("get-my-notifications:" + userId, properties.getGetMyNotifications());
    }

    public void checkGetUnreadCount(String userId) {
        enforce("get-unread-count:" + userId, properties.getGetUnreadCount());
    }

    public void checkMarkAllAsRead(String userId) {
        enforce("mark-all-as-read:" + userId, properties.getMarkAllAsRead());
    }

    private void enforce(String keySuffix, NotificationRateLimitProperties.Rule rule) {
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
