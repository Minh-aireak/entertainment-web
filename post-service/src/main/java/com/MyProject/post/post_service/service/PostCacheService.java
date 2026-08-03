package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCacheService {
    RedisService redisService;

    private String getPostsCacheKey(String userId, String type, int page, int size) {
        return String.format("user:%s:posts:%s:%d:%d", userId, type, page, size);
    }

    public PageResponse<ScheduleResponse> getCachedPosts(String userId, String type, int page, int size) {
        String cacheKey = getPostsCacheKey(userId, type, page, size);
        try {
            PageResponse<ScheduleResponse> cached = redisService.get(cacheKey, new TypeReference<PageResponse<ScheduleResponse>>() {});
            if (cached != null) {
                log.info("Returning cached posts for user {}", userId);
                return cached;
            }
        } catch (Exception e) {
            log.error("Error accessing Redis cache", e);
        }
        return null;
    }

    public void cachePosts(String userId, String type, int page, int size, PageResponse<ScheduleResponse> result) {
        String cacheKey = getPostsCacheKey(userId, type, page, size);
        try {
            redisService.setWithExpiration(cacheKey, result, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error saving to Redis cache", e);
        }
    }

    public void invalidateAllUserPosts(String userId) {
        String pattern = String.format("user:%s:posts:*", userId);
        try {
            redisService.deletePattern(pattern);
            log.info("Invalidated all post cache for user {}", userId);
        } catch (Exception e) {
            log.error("Error invalidating post cache for user {}", userId, e);
        }
    }
}
