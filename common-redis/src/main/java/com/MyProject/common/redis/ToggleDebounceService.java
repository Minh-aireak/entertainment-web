package com.MyProject.common.redis;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

/**
 * Shared toggle+debounce primitive for like/unlike-style notifications (post likes, comment
 * reactions, film rating likes, ...). Callers are responsible for writing the actual toggle state
 * (DB row, Redis hash, whatever); this class only owns the "did the dust settle on liked?"
 * decision for notifications. Every {@link #schedule} call pushes a member's due time forward, so
 * a like/unlike/like burst on the same (actor, target) pair within the debounce window collapses
 * into a single flush - the flush job is expected to re-read the real toggle state before
 * deciding whether to actually notify.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToggleDebounceService {
    RedisTemplate<String, String> redisTemplate;

    private static final DefaultRedisScript<List> DEQUEUE_SCRIPT = buildDequeueScript();

    @SuppressWarnings("unchecked")
    private static DefaultRedisScript<List> buildDequeueScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2]) " +
                "if #due > 0 then redis.call('ZREM', KEYS[1], unpack(due)) end " +
                "return due"
        );
        script.setResultType((Class) List.class);
        return script;
    }

    /** Schedules (or reschedules, resetting the debounce timer) a flush for {@code memberId}. */
    public void schedule(String queueKey, String memberId, long delaySeconds) {
        double dueAt = (System.currentTimeMillis() / 1000.0) + delaySeconds;
        redisTemplate.opsForZSet().add(queueKey, memberId, dueAt);
    }

    /** Atomically pops up to {@code batchSize} members whose debounce window has elapsed. */
    @SuppressWarnings("unchecked")
    public List<String> pollDue(String queueKey, int batchSize) {
        double now = System.currentTimeMillis() / 1000.0;
        List<String> due = redisTemplate.execute(
                DEQUEUE_SCRIPT,
                Collections.singletonList(queueKey),
                String.valueOf(now),
                String.valueOf(batchSize)
        );
        return due == null ? Collections.emptyList() : due;
    }
}
