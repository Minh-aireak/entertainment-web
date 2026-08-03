package com.MyProject.identity.identity_service.configuration;

import org.springframework.util.AntPathMatcher;

public final class IdentityRequestClassifier {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String[] PUBLIC_PATTERNS = {
            "/auth/login",
            "/auth/introspect",
            "/auth/outbound/google",
            "/auth/refresh-token",
            "/auth/logout",
            "/users/forgot-password",
            "/users/reset-password",
            "/users/registration/**"
    };

    private IdentityRequestClassifier() {
    }

    public static boolean isPublicRequest(String path) {
        for (String pattern : PUBLIC_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
