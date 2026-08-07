package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.post.post_service.configuration.PostRateLimitProperties;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "post:rate-limit:";

    private final RedisService redisService;
    private final PostRateLimitProperties properties;

    public void checkPostWrite(String userId) {
        enforce("post-write:" + userId, properties.getPostWrite());
    }

    public void checkPostUpdate(String userId) {
        enforce("post-update:" + userId, properties.getPostUpdate());
    }

    public void checkPostDelete(String userId) {
        enforce("post-delete:" + userId, properties.getPostDelete());
    }

    public void checkPostRead(String userId) {
        enforce("post-read:" + userId, properties.getPostRead());
    }

    public void checkPostSearch(String userId) {
        enforce("post-search:" + userId, properties.getPostSearch());
    }

    public void checkPostCount(String userId) {
        enforce("post-count:" + userId, properties.getPostCount());
    }

    public void checkPostRandom(String userId) {
        enforce("post-random:" + userId, properties.getPostRandom());
    }

    private void enforce(String keySuffix, PostRateLimitProperties.Rule rule) {
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
