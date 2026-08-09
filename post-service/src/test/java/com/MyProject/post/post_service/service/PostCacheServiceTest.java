package com.MyProject.post.post_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.post.post_service.dto.response.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostCacheServiceTest {

    @Mock RedisService redisService;

    PostCacheService postCacheService;

    @BeforeEach
    void setUp() {
        postCacheService = new PostCacheService(redisService);
    }

    @Test
    void getCachedPosts_cacheHit_returnsCachedValue() {
        PageResponse<PostResponse> cached = PageResponse.<PostResponse>builder().currentPage(1).build();
        when(redisService.get(eq("user:user-1:posts:1:10"), any())).thenReturn(cached);

        PageResponse<PostResponse> result = postCacheService.getCachedPosts("user-1", 1, 10);

        assertThat(result).isSameAs(cached);
    }

    @Test
    void getCachedPosts_cacheMiss_returnsNull() {
        when(redisService.get(eq("user:user-1:posts:1:10"), any())).thenReturn(null);

        assertThat(postCacheService.getCachedPosts("user-1", 1, 10)).isNull();
    }

    @Test
    void getCachedPosts_redisThrows_returnsNullInsteadOfPropagating() {
        when(redisService.get(eq("user:user-1:posts:1:10"), any())).thenThrow(new RuntimeException("redis down"));

        assertThat(postCacheService.getCachedPosts("user-1", 1, 10)).isNull();
    }

    @Test
    void cachePosts_happyPath_setsWithTenMinuteExpiration() {
        PageResponse<PostResponse> result = PageResponse.<PostResponse>builder().currentPage(1).build();

        postCacheService.cachePosts("user-1", 1, 10, result);

        verify(redisService).setWithExpiration("user:user-1:posts:1:10", result, 10, TimeUnit.MINUTES);
    }

    @Test
    void cachePosts_redisThrows_doesNotPropagate() {
        PageResponse<PostResponse> result = PageResponse.<PostResponse>builder().build();
        doThrow(new RuntimeException("redis down")).when(redisService)
                .setWithExpiration(anyString(), any(), anyLong(), any());

        assertThatCode(() -> postCacheService.cachePosts("user-1", 1, 10, result)).doesNotThrowAnyException();
    }

    @Test
    void invalidateAllUserPosts_happyPath_deletesPattern() {
        postCacheService.invalidateAllUserPosts("user-1");

        verify(redisService).deletePattern("user:user-1:posts:*");
    }

    @Test
    void invalidateAllUserPosts_redisThrows_doesNotPropagate() {
        doThrow(new RuntimeException("redis down")).when(redisService).deletePattern(anyString());

        assertThatCode(() -> postCacheService.invalidateAllUserPosts("user-1")).doesNotThrowAnyException();
    }
}
