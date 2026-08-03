package com.MyProject.chat_service.service;

import com.MyProject.chat_service.configuration.ChatRateLimitProperties;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "chat:rate-limit:";

    private final RedisService redisService;
    private final ChatRateLimitProperties properties;

    public void checkMessageWrite(String userId, String conversationId) {
        enforce("message-write:" + userId + ":" + conversationId, properties.getMessageWrite());
    }

    public void checkMessageUpdate(String userId) {
        enforce("message-update:" + userId, properties.getMessageUpdate());
    }

    public void checkMessageDelete(String userId) {
        enforce("message-delete:" + userId, properties.getMessageDelete());
    }

    public void checkMessageRead(String userId, String conversationId) {
        enforce("message-read:" + userId + ":" + conversationId, properties.getMessageRead());
    }

    public void checkMessageSearch(String userId, String conversationId) {
        enforce("message-search:" + userId + ":" + conversationId, properties.getMessageSearch());
    }

    public void checkSeen(String userId, String conversationId) {
        enforce("seen:" + userId + ":" + conversationId, properties.getSeen());
    }

    public void checkUnreadCount(String userId) {
        enforce("unread-count:" + userId, properties.getUnreadCount());
    }

    public void checkConversationWrite(String userId) {
        enforce("conversation-write:" + userId, properties.getConversationWrite());
    }

    public void checkConversationRead(String userId) {
        enforce("conversation-read:" + userId, properties.getConversationRead());
    }

    public void checkConversationSearch(String userId) {
        enforce("conversation-search:" + userId, properties.getConversationSearch());
    }

    private void enforce(String keySuffix, ChatRateLimitProperties.Rule rule) {
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
            // Fail-open so Redis issues do not block chat APIs entirely.
            log.warn("Skip custom rate limit check for key {} because Redis is unavailable.", keySuffix, exception);
        }
    }
}
