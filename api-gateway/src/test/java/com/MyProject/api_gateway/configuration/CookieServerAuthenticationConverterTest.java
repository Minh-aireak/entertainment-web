package com.MyProject.api_gateway.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServerAuthenticationConverterTest {

    private static final String PROTECTED_PATH = "/api/v1/friends/request";
    private static final String PUBLIC_PATH = "/api/v1/identities/auth/login";

    private final CookieServerAuthenticationConverter converter = new CookieServerAuthenticationConverter();

    @Test
    void convert_publicPath_returnsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(PUBLIC_PATH)
                        .header("Authorization", "Bearer some-token")
                        .build());

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isNull();
    }

    @Test
    void convert_authorizationHeaderPresent_usesHeaderToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, PROTECTED_PATH)
                        .header("Authorization", "Bearer header-token")
                        .build());

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication)
                .isInstanceOf(org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken.class);
        assertThat(((org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken) authentication).getToken())
                .isEqualTo("header-token");
    }

    @Test
    void convert_noHeader_usesCookieToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, PROTECTED_PATH)
                        .cookie(new HttpCookie("access_token", "cookie-token"))
                        .build());

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isInstanceOf(BearerTokenAuthenticationToken.class);
        assertThat(((BearerTokenAuthenticationToken) authentication).getToken()).isEqualTo("cookie-token");
    }

    @Test
    void convert_noHeaderNoCookie_returnsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, PROTECTED_PATH).build());

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isNull();
    }

    @Test
    void convert_blankCookieValue_returnsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, PROTECTED_PATH)
                        .cookie(new HttpCookie("access_token", ""))
                        .build());

        Authentication authentication = converter.convert(exchange).block();

        assertThat(authentication).isNull();
    }
}
