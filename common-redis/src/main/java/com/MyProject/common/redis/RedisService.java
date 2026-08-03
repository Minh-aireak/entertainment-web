package com.MyProject.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisService {
    RedisTemplate<String, String> redisTemplate;
    ObjectMapper objectMapper;

    public void set(String key, Object value) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, stringValue);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public void setWithExpiration(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, stringValue, timeout, unit);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public String getAsString(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T get(String key, TypeReference<T> typeReference) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing object from JSON", e);
            throw new RuntimeException(e);
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void expire(String key, long timeout, TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }

    public void deletePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void listLeftPush(String key, Object value) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForList().leftPush(key, stringValue);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    public <T> List<T> listRange(String key, long start, long end, TypeReference<T> typeReference) {
        List<String> values = redisTemplate.opsForList().range(key, start, end);
        if (values == null) return List.of();
        
        return values.stream().map(value -> {
            try {
                return objectMapper.readValue(value, typeReference);
            } catch (JsonProcessingException e) {
                log.error("Error deserializing object from JSON", e);
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    public <T> List<T> multiGet(List<String> keys, TypeReference<T> typeReference) {
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) return List.of();

        return values.stream()
                .map(value -> {
                    if (value == null) {
                        return null;
                    }
                    try {
                        return objectMapper.readValue(value, typeReference);
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing object from JSON", e);
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    public void addSet(String key, String value) { 
        redisTemplate.opsForSet().add(key, value);
    }

    public void removeSet(String key, String value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    public Set<String> getSetMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    public boolean isMember(String key, String value) {
        Boolean result = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(result);
    }

    public Long setSize(String key) { 
        return redisTemplate.opsForSet().size(key); 
    }

    public void zAdd(String key, Object value, double score) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForZSet().add(key, stringValue, score);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public <T> Set<T> zRevRangeByScore(String key, double minScore, double maxScore, long offset, int size, TypeReference<T> typeReference) {
        Set<String> values = redisTemplate.opsForZSet().reverseRangeByScore(key, minScore, maxScore, offset, size);
        if (values == null) return Set.of();

        return values.stream().map(value -> {
            try {
                return objectMapper.readValue(value, typeReference);
            } catch (JsonProcessingException e) {
                log.error("Error deserializing object from JSON", e);
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    public void zRemove(String key, Object value) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForZSet().remove(key, stringValue);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public <T> Set<T> zRevRange(String key, long start, long end, TypeReference<T> typeReference) {
        Set<String> values = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (values == null) return Set.of();

        return values.stream().map(value -> {
            try {
                return objectMapper.readValue(value, typeReference);
            } catch (JsonProcessingException e) {
                log.error("Error deserializing object from JSON", e);
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }

    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    public void hashPut(String key, String field, Object value) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            redisTemplate.opsForHash().put(key, field, stringValue);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public <T> T hashGet(String key, String field, TypeReference<T> typeReference) {
        Object value = redisTemplate.opsForHash().get(key, field);
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value.toString(), typeReference);
        } catch (JsonProcessingException e) {
            log.error("Error deserializing object from JSON", e);
            throw new RuntimeException(e);
        }
    }

    public void hashIncrement(String key, String field, long delta) {
        redisTemplate.opsForHash().increment(key, field, delta);
    }

    public java.util.Map<Object, Object> hashGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public void hashDelete(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }

    public Double zScore(String key, Object value) {
        try {
            String stringValue = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            return redisTemplate.opsForZSet().score(key, stringValue);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new RuntimeException(e);
        }
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

    public boolean tryAcquireRateLimit(String key, long limit, long windowSeconds) {
        String script =
                "local current = redis.call('INCR', KEYS[1]) " +
                "if current == 1 then " +
                "  redis.call('EXPIRE', KEYS[1], ARGV[2]) " +
                "end " +
                "if current > tonumber(ARGV[1]) then " +
                "  return 0 " +
                "end " +
                "return 1";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);

        Long result = redisTemplate.execute(
                redisScript,
                List.of(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds)
        );
        return Long.valueOf(1L).equals(result);
    }

    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }
}
