package com.MyProject.api_gateway.configuration;

import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.util.StringUtils;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class CookieServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    private final ServerBearerTokenAuthenticationConverter headerAuthenticationConverter =
            new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();
        if (GatewayRequestClassifier.isPublicRequest(path, method)) {
            return Mono.empty();
        }

        String authorizationHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authorizationHeader)) {
            return headerAuthenticationConverter.convert(exchange);
        }

        HttpCookie accessTokenCookie = exchange.getRequest().getCookies().getFirst(ACCESS_TOKEN_COOKIE_NAME);
        if (accessTokenCookie != null && StringUtils.hasText(accessTokenCookie.getValue())) {
            return Mono.just(new BearerTokenAuthenticationToken(accessTokenCookie.getValue()));
        }
        return Mono.empty();
    }
}
