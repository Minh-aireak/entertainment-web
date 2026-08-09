package com.MyProject.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @Test
    void resolve_headerPresent_returnsHeaderToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/films");
        request.addHeader("Authorization", "Bearer header-token");
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", "cookie-token"));

        String resolved = resolver.resolve(request);

        assertThat(resolved).isEqualTo("header-token");
    }

    @Test
    void resolve_noHeader_returnsCookieToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/films");
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", "cookie-token"));

        String resolved = resolver.resolve(request);

        assertThat(resolved).isEqualTo("cookie-token");
    }

    @Test
    void resolve_noHeaderNoCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/films");

        String resolved = resolver.resolve(request);

        assertThat(resolved).isNull();
    }

    @Test
    void resolve_blankCookieValue_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/films");
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", ""));

        String resolved = resolver.resolve(request);

        assertThat(resolved).isNull();
    }

    @Test
    void resolve_otherCookiesPresent_ignoresThemAndReturnsAccessTokenCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/films");
        request.setCookies(
                new jakarta.servlet.http.Cookie("session_id", "irrelevant"),
                new jakarta.servlet.http.Cookie("access_token", "cookie-token")
        );

        String resolved = resolver.resolve(request);

        assertThat(resolved).isEqualTo("cookie-token");
    }

    @Test
    void resolve_publicRequestMatcher_returnsNullEvenWithToken() {
        CookieBearerTokenResolver publicResolver =
                new CookieBearerTokenResolver((path, request) -> path.equals("/api/v1/public"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/public");
        request.addHeader("Authorization", "Bearer header-token");

        String resolved = publicResolver.resolve(request);

        assertThat(resolved).isNull();
    }

    @Test
    void resolve_contextPathIsStrippedBeforeMatching() {
        CookieBearerTokenResolver publicResolver =
                new CookieBearerTokenResolver((path, request) -> path.equals("/films"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI("/api/films");
        request.addHeader("Authorization", "Bearer header-token");

        String resolved = publicResolver.resolve(request);

        assertThat(resolved).isNull();
    }
}
