package com.MyProject.api_gateway.configuration;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

public final class GatewayRequestClassifier {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String[] PUBLIC_PATTERNS = {
            "/api/v1/identities/auth/login",
            "/api/v1/identities/auth/introspect",
            "/api/v1/identities/auth/outbound/google",
            "/api/v1/identities/auth/refresh-token",
            "/api/v1/identities/auth/logout",
            "/api/v1/identities/users/forgot-password",
            "/api/v1/identities/users/reset-password",
            "/api/v1/identities/users/registration",
            "/api/v1/files/media/download/**",
            "/api/v1/files/media/info/**",
            "/api/v1/sockets/**"
    };

    private static final String[] AUTHENTICATED_GET_PATTERNS = {
            "/api/v1/films/follows/my"
    };

    private GatewayRequestClassifier() {
    }

    public static boolean isPublicRequest(String path, HttpMethod method) {
        for (String pattern : PUBLIC_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }

        if (HttpMethod.GET.equals(method)) {
            for (String pattern : AUTHENTICATED_GET_PATTERNS) {
                if (PATH_MATCHER.match(pattern, path)) {
                    return false;
                }
            }
        }

        return HttpMethod.GET.equals(method) && PATH_MATCHER.match("/api/v1/films/**", path);
    }
}
