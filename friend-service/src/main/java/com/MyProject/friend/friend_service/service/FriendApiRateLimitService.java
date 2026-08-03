package com.MyProject.friend.friend_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.friend.friend_service.configuration.FriendRateLimitProperties;
import com.MyProject.friend.friend_service.exception.AppException;
import com.MyProject.friend.friend_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "friend:rate-limit:";

    private final RedisService redisService;
    private final FriendRateLimitProperties properties;

    public void checkSendFriendRequest(String userId) {
        enforce("send-friend-request:" + userId, properties.getSendFriendRequest());
    }

    public void checkAcceptFriendRequest(String userId) {
        enforce("accept-friend-request:" + userId, properties.getAcceptFriendRequest());
    }

    public void checkUnfriend(String userId) {
        enforce("unfriend:" + userId, properties.getUnfriend());
    }

    public void checkReadFriends(String userId) {
        enforce("read-friends:" + userId, properties.getReadFriends());
    }

    public void checkSearchFriends(String userId) {
        enforce("search-friends:" + userId, properties.getSearchFriends());
    }

    public void checkCountFriends(String userId) {
        enforce("count-friends:" + userId, properties.getCountFriends());
    }

    public void checkReadFriendRequests(String userId) {
        enforce("read-friend-requests:" + userId, properties.getReadFriendRequests());
    }

    private void enforce(String keySuffix, FriendRateLimitProperties.Rule rule) {
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
