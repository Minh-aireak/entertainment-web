package com.MyProject.api_gateway.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEndpointRateLimitFilterTest {

    private static final String PUBLIC_LOGIN_PATH = "/api/v1/identities/auth/login";

    @Mock
    ReactiveStringRedisTemplate redisTemplate;

    @Mock
    ReactiveValueOperations<String, String> valueOperations;

    @Mock
    GatewayFilterChain chain;

    PublicEndpointRateLimitProperties properties;
    PublicEndpointRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new PublicEndpointRateLimitProperties();
        properties.setEnabled(true);
        properties.setRules(List.of(loginRule()));
        filter = new PublicEndpointRateLimitFilter(new ObjectMapper(), properties, redisTemplate);
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private PublicEndpointRateLimitProperties.Rule loginRule() {
        PublicEndpointRateLimitProperties.Rule rule = new PublicEndpointRateLimitProperties.Rule();
        rule.setName("login");
        rule.setPathPattern(PUBLIC_LOGIN_PATH);
        rule.setMethod("POST");
        rule.setLimit(5);
        rule.setWindowSeconds(60);
        return rule;
    }

    private ServerWebExchange exchangeFor(HttpMethod method, String path, String... xForwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (xForwardedFor.length > 0) {
            builder.header("X-Forwarded-For", xForwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void optionsRequest_skipsRateLimiting_callsChainDirectly() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.OPTIONS, PUBLIC_LOGIN_PATH);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void nonMatchingPath_callsChainDirectly_withoutTouchingRedis() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/v1/films/some-id");

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void disabledProperties_callsChainDirectly_evenForPublicPath() {
        properties.setEnabled(false);
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void invalidRule_missingLimit_isSkipped_callsChainDirectly() {
        PublicEndpointRateLimitProperties.Rule invalidRule = loginRule();
        invalidRule.setLimit(0);
        properties.setRules(List.of(invalidRule));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void underLimit_incrementsCounterAndSetsHeaders_callsChain() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(3L));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH, "203.0.113.5");

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("2");
    }

    @Test
    void firstRequestInWindow_setsExpiryOnBucket() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH, "203.0.113.5");

        filter.filter(exchange, chain).block();

        verify(redisTemplate).expire(eq("gateway:rate-limit:login:203.0.113.5"), eq(Duration.ofSeconds(60)));
        verify(chain).filter(exchange);
    }

    @Test
    void overLimit_returns429_doesNotCallChain() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH, "203.0.113.5");

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotNull();
        String body = ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
        assertThat(body).contains("\"rule\":\"login\"");
    }

    @Test
    void clientId_fallsBackToUnknown_whenNoForwardedForAndNoRemoteAddress() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH);

        filter.filter(exchange, chain).block();

        verify(valueOperations).increment("gateway:rate-limit:login:unknown");
    }

    @Test
    void clientId_usesFirstEntryOfForwardedForHeader() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, PUBLIC_LOGIN_PATH, "203.0.113.5, 10.0.0.1");

        filter.filter(exchange, chain).block();

        verify(valueOperations).increment("gateway:rate-limit:login:203.0.113.5");
    }
}
