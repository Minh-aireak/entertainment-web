package com.MyProject.api_gateway.configuration;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class CookieToAuthorizationHeaderFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (GatewayRequestClassifier.isPublicRequest(path, method)) {
            return chain.filter(exchange);
        }

        if (StringUtils.hasText(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return chain.filter(exchange);
        }

        HttpCookie accessTokenCookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE_NAME);
        if (accessTokenCookie == null || !StringUtils.hasText(accessTokenCookie.getValue())) {
            return chain.filter(exchange);
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request -> request.headers(headers ->
                        headers.setBearerAuth(accessTokenCookie.getValue())))
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
