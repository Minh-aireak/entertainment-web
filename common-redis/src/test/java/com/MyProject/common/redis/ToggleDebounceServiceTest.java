package com.MyProject.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleDebounceServiceTest {

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    ZSetOperations<String, String> zSetOperations;

    ToggleDebounceService toggleDebounceService;

    @BeforeEach
    void setUp() {
        toggleDebounceService = new ToggleDebounceService(redisTemplate);
    }

    @Test
    void schedule_addsMemberToZsetWithDueTimeInFuture() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        long before = System.currentTimeMillis() / 1000;

        toggleDebounceService.schedule("queue:post-like", "post-1", 30L);

        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations).add(eq("queue:post-like"), eq("post-1"), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(before + 30.0, within(2.0));
    }

    @Test
    void pollDue_returnsMembersFromScript() {
        List<String> due = List.of("post-1", "post-2");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(due);

        List<String> result = toggleDebounceService.pollDue("queue:post-like", 10);

        assertThat(result).containsExactly("post-1", "post-2");
    }

    @Test
    void pollDue_scriptReturnsNull_returnsEmptyList() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(null);

        List<String> result = toggleDebounceService.pollDue("queue:post-like", 10);

        assertThat(result).isEmpty();
    }

    @Test
    void pollDue_passesQueueKeyAndBatchSizeToScript() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(Collections.emptyList());

        toggleDebounceService.pollDue("queue:comment-reaction", 25);

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("queue:comment-reaction")),
                any(),
                eq("25")
        );
    }
}
