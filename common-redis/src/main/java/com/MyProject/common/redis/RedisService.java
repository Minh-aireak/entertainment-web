package com.MyProject.common.redis;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisService {
    RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void setWithExpiration(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deletePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void listLeftPush(String key, Object value) {
        redisTemplate.opsForList().leftPush(key, value);
    }

    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    public List<Object> listRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public List<Object> multiGet(List<String> keys) {
        return redisTemplate.opsForValue().multiGet(keys);
    }

    public void addSet(String key, String value) { 
        redisTemplate.opsForSet().add(key, value);
    }

    public void removeSet(String key, String value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getSetMembers(String key) {
        return (Set<String>) (Set<?>) redisTemplate.opsForSet().members(key);
    }

    public boolean isMember(String key, String value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    public Long setSize(String key) { 
        return redisTemplate.opsForSet().size(key); 
    }

    public void zAdd(String key, Object value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> zRevRangeByScore(String key, double minScore, double maxScore, long offset, int size) {
        return redisTemplate.opsForZSet().reverseRangeByScore(key, minScore, maxScore, offset, size);
    }

    public void zRemove(String key, Object value) {
        redisTemplate.opsForZSet().remove(key, value);
    }

    public Set<Object> zRevRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    public void resetUnreadCount(String unreadKey, String totalUnreadKey) {
        String script = 
                "local currentUnread = redis.call('get', KEYS[1]) " +
                "if currentUnread then " +
                "  redis.call('set', KEYS[1], 0) " +
                "  local totalUnread = redis.call('get', KEYS[2]) " +
                "  if totalUnread then " +
                "    local newTotal = math.max(0, tonumber(totalUnread) - tonumber(currentUnread)) " +
                "    redis.call('set', KEYS[2], newTotal) " +
                "  end " +
                "end " +
                "return 1";
        
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        
        redisTemplate.execute(redisScript, List.of(unreadKey, totalUnreadKey));
    }
}
