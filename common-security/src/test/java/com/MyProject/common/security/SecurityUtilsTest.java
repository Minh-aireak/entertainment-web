package com.MyProject.common.security;

import com.MyProject.common.exception.AppException;
import com.MyProject.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "HS512"),
                claims
        );
    }

    @Test
    void getCurrentUserId_validJwtAuthentication_returnsUserId() {
        Jwt jwt = jwtWithClaims(Map.of("userId", "user-123"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        String userId = SecurityUtils.getCurrentUserId();

        assertThat(userId).isEqualTo("user-123");
    }

    @Test
    void getCurrentUserId_missingUserIdClaim_throwsUnauthenticated() {
        Jwt jwt = jwtWithClaims(Collections.singletonMap("sub", "test-user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void getCurrentUserId_notJwtAuthentication_throwsUnauthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "password"));

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void getCurrentUserId_noAuthentication_throwsUnauthenticated() {
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
