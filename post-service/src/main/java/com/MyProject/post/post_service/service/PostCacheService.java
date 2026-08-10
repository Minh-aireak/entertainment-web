package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.post.post_service.dto.response.PostResponse;
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

    private String getPostsCacheKey(String userId, int page, int size) {
        return String.format("user:%s:posts:%d:%d", userId, page, size);
    }

    public PageResponse<PostResponse> getCachedPosts(String userId, int page, int size) {
        String cacheKey = getPostsCacheKey(userId, page, size);
        try {
            PageResponse<PostResponse> cached = redisService.get(cacheKey, new TypeReference<PageResponse<PostResponse>>() {});
            if (cached != null) {
                log.info("Returning cached posts for user {}", userId);
                return cached;
            }
        } catch (Exception e) {
            log.error("Error accessing Redis cache", e);
        }
        return null;
    }

    public void cachePosts(String userId, int page, int size, PageResponse<PostResponse> result) {
        String cacheKey = getPostsCacheKey(userId, page, size);
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
