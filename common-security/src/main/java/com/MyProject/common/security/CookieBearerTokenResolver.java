package com.MyProject.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.StringUtils;

import java.util.function.BiPredicate;

public class CookieBearerTokenResolver implements BearerTokenResolver {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final BearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();
    private final BiPredicate<String, HttpServletRequest> publicRequestMatcher;

    public CookieBearerTokenResolver() {
        this((path, request) -> false);
    }

    public CookieBearerTokenResolver(BiPredicate<String, HttpServletRequest> publicRequestMatcher) {
        this.publicRequestMatcher = publicRequestMatcher;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        if (publicRequestMatcher.test(requestPath, request)) {
            return null;
        }

        String headerToken = headerTokenResolver.resolve(request);
        if (StringUtils.hasText(headerToken)) {
            return headerToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
