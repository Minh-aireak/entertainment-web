package com.MyProject.identity.identity_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.identity.identity_service.configuration.IdentityRateLimitProperties;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityApiRateLimitService {
    private static final String RATE_LIMIT_PREFIX = "identity:rate-limit:";

    private final RedisService redisService;
    private final IdentityRateLimitProperties properties;

    public void checkUserRegistration(String email) {
        enforce("user-registration:" + email, properties.getUserRegistration());
    }

    public void checkChangePassword(String userId) {
        enforce("change-password:" + userId, properties.getChangePassword());
    }

    public void checkForgotPassword(String email) {
        enforce("forgot-password:" + email, properties.getForgotPassword());
    }

    public void checkResetPassword(String token) {
        enforce("reset-password:" + token, properties.getResetPassword());
    }

    public void checkUserManagement(String userId) {
        enforce("user-management:" + userId, properties.getUserManagement());
    }

    public void checkRoleManagement(String userId) {
        enforce("role-management:" + userId, properties.getRoleManagement());
    }

    public void checkLogin(String username) {
        enforce("login:" + username, properties.getLogin());
    }

    public void checkIntrospect(String userIdOrToken) {
        enforce("introspect:" + userIdOrToken, properties.getIntrospect());
    }

    public void checkLogout(String userId) {
        enforce("logout:" + userId, properties.getLogout());
    }

    public void checkRefreshToken(String userIdOrToken) {
        enforce("refresh-token:" + userIdOrToken, properties.getRefreshToken());
    }

    public void checkOutboundGoogle(String code) {
        enforce("outbound-google:" + code, properties.getOutboundGoogle());
    }

    private void enforce(String keySuffix, IdentityRateLimitProperties.Rule rule) {
        try {
            long windowSeconds = Math.max(1, rule.getLimitRefreshPeriod().toSeconds());
            boolean allowed = redisService.tryAcquireRateLimit(
                    RATE_LIMIT_PREFIX + keySuffix,
                    rule.getLimitForPeriod(),
                    windowSeconds
            );
            if (!allowed) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Skip custom rate limit check for key {} because Redis is unavailable.", keySuffix, exception);
        }
    }
}
