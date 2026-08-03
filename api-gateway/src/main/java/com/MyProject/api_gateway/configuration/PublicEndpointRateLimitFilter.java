package com.MyProject.api_gateway.configuration;

import com.MyProject.common.dto.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PublicEndpointRateLimitFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    private static final String UNKNOWN_CLIENT = "unknown";
    private static final String KEY_PREFIX = "gateway:rate-limit:";

    ObjectMapper objectMapper;
    PublicEndpointRateLimitProperties properties;
    ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        PublicEndpointRateLimitProperties.Rule rule = resolveRule(path, method);
        if (rule == null) {
            return chain.filter(exchange);
        }

        String clientId = resolveClientId(exchange);
        String bucketKey = buildBucketKey(rule, clientId);
        long now = Instant.now().getEpochSecond();
        long resetAt = now + rule.getWindowSeconds();

        return redisTemplate.opsForValue()
                .increment(bucketKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request, set expiration
                        return redisTemplate.expire(bucketKey, Duration.ofSeconds(rule.getWindowSeconds()))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
                    responseHeaders.set(RATE_LIMIT_LIMIT_HEADER, Integer.toString(rule.getLimit()));
                    responseHeaders.set(RATE_LIMIT_REMAINING_HEADER, Integer.toString(Math.max(rule.getLimit() - count.intValue(), 0)));
                    responseHeaders.set(RATE_LIMIT_RESET_HEADER, Long.toString(Math.max(resetAt - now, 0)));

                    if (count <= rule.getLimit()) {
                        return chain.filter(exchange);
                    }

                    responseHeaders.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(resetAt - now, 1)));
                    return writeTooManyRequests(exchange.getResponse(), rule);
                });
    }

    private PublicEndpointRateLimitProperties.Rule resolveRule(String path, HttpMethod method) {
        if (!properties.isEnabled() || method == null || !GatewayRequestClassifier.isPublicRequest(path, method)) {
            return null;
        }

        List<PublicEndpointRateLimitProperties.Rule> rules = properties.getRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (PublicEndpointRateLimitProperties.Rule rule : rules) {
            if (!isValidRule(rule)) {
                continue;
            }

            if (!PATH_MATCHER.match(rule.getPathPattern(), path)) {
                continue;
            }

            if (!matchesMethod(rule, method)) {
                continue;
            }

            return rule;
        }

        return null;
    }

    private boolean isValidRule(PublicEndpointRateLimitProperties.Rule rule) {
        return rule != null
                && StringUtils.hasText(rule.getName())
                && StringUtils.hasText(rule.getPathPattern())
                && rule.getLimit() > 0
                && rule.getWindowSeconds() > 0;
    }

    private boolean matchesMethod(PublicEndpointRateLimitProperties.Rule rule, HttpMethod method) {
        return !StringUtils.hasText(rule.getMethod()) || method.name().equalsIgnoreCase(rule.getMethod());
    }

    private String buildBucketKey(PublicEndpointRateLimitProperties.Rule rule, String clientId) {
        return KEY_PREFIX + rule.getName() + ":" + clientId;
    }

    private String resolveClientId(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return UNKNOWN_CLIENT;
        }

        return remoteAddress.getAddress().getHostAddress();
    }

    private Mono<Void> writeTooManyRequests(ServerHttpResponse response, PublicEndpointRateLimitProperties.Rule rule) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Map<String, Object>> apiResponse = ApiResponse.<Map<String, Object>>builder()
                .code(1429)
                .message("Too many requests")
                .result(Map.of(
                        "rule", rule.getName(),
                        "limit", rule.getLimit(),
                        "windowSeconds", rule.getWindowSeconds()
                ))
                .build();

        try {
            String body = objectMapper.writeValueAsString(apiResponse);
            DataBufferFactory bufferFactory = response.bufferFactory();
            DataBuffer buffer = bufferFactory.wrap(body.getBytes());
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException ex) {
            return Mono.error(ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
