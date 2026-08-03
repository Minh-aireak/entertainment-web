package com.MyProject.common.security;

import com.MyProject.common.exception.AppException;
import com.MyProject.common.exception.ErrorCode;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Objects;

@UtilityClass
public class SecurityUtils {
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String userId = jwt.getClaim("userId");
        if (Objects.isNull(userId)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
