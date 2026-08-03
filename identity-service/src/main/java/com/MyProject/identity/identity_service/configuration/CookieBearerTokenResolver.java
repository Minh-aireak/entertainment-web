package com.MyProject.identity.identity_service.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.util.StringUtils;

public class CookieBearerTokenResolver implements BearerTokenResolver {
    private final BearerTokenResolver headerTokenResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        if (IdentityRequestClassifier.isPublicRequest(requestPath)) {
            return null;
        }

        String headerToken = headerTokenResolver.resolve(request);
        if (StringUtils.hasText(headerToken)) {
            return headerToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
            }
        }
        return null;
    }
}
