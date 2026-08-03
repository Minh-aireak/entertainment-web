package com.MyProject.comment_service.service;

import com.MyProject.comment_service.configuration.CommentRateLimitProperties;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "comment:rate-limit:";

    private final RedisService redisService;
    private final CommentRateLimitProperties properties;

    public void checkCommentWrite(String userId, String sourceId) {
        enforce("comment-write:" + userId + ":" + sourceId, properties.getCommentWrite());
    }

    public void checkCommentRead(String sourceId) {
        enforce("comment-read:" + sourceId, properties.getCommentRead());
    }

    public void checkCommentUpdate(String userId) {
        enforce("comment-update:" + userId, properties.getCommentUpdate());
    }

    public void checkCommentDelete(String userId) {
        enforce("comment-delete:" + userId, properties.getCommentDelete());
    }

    private void enforce(String keySuffix, CommentRateLimitProperties.Rule rule) {
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
