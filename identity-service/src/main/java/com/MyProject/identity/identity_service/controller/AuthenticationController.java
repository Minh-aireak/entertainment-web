package com.MyProject.identity.identity_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.identity.identity_service.service.IdentityApiRateLimitService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.service.AuthenticationService;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String JSESSIONID_COOKIE = "JSESSIONID";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String COOKIE_PATH = "/";
    private static final String SAME_SITE_POLICY = "Lax";

    AuthenticationService authenticationService;
    IdentityApiRateLimitService identityApiRateLimitService;

    @NonFinal
    @Value("${jwt.valid-duration}")
    long validDuration;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    long refreshableDuration;

    @NonFinal
    @Value("${app.cookie.secure:false}")
    boolean secureCookie;

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, validDuration);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, refreshableDuration);
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        clearCookie(response, REFRESH_TOKEN_COOKIE);
    }

    private void clearSessionCookies(HttpServletResponse response) {
        clearCookie(response, JSESSIONID_COOKIE);
        clearCookie(response, SESSION_COOKIE);
    }

    private void addCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge((int) maxAgeSeconds);
        cookie.setAttribute("SameSite", SAME_SITE_POLICY);
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", SAME_SITE_POLICY);
        response.addCookie(cookie);
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    @PostMapping("/login")

    ApiResponse<Void> authenticate(@RequestBody @Valid AuthenticationRequest request, HttpServletResponse response) {
        identityApiRateLimitService.checkLogin(request.getUsername());
        var result = authenticationService.authentication(request);
        setAccessTokenCookie(response, result.getToken());
        setRefreshTokenCookie(response, result.getRefreshToken());
        return ApiResponse.<Void>builder()
                .message("Login success!")
                .build();
    }

    @PostMapping("/introspect")

    ApiResponse<IntrospectResponse> introspect(
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) {
        String accessToken = StringUtils.hasText(token) ? token : getAccessTokenFromCookie(request);
        if (!StringUtils.hasText(accessToken)) {
            throw new AppException(ErrorCode.ACCESS_TOKEN_MISSING);
        }
        identityApiRateLimitService.checkIntrospect(accessToken);
        var result = authenticationService.introspectResponse(accessToken);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    @PostMapping("/logout")

    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            identityApiRateLimitService.checkLogout(refreshToken);
            authenticationService.logout(refreshToken);
        }
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
        clearSessionCookies(response);
        return ApiResponse.<Void>builder()
                .message("Logout success!")
                .build();
    }

    @PostMapping("/refresh-token")

    ApiResponse<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        identityApiRateLimitService.checkRefreshToken(refreshToken);
        var result = authenticationService.refreshToken(refreshToken);
        setAccessTokenCookie(response, result.getToken());
        setRefreshTokenCookie(response, result.getRefreshToken());
        return ApiResponse.<Void>builder()
                .message("Token refreshed!")
                .build();
    }

    @PostMapping("/outbound/google")

    ApiResponse<Void> outboundAuthenticate(@RequestParam("code") String code, HttpServletResponse response) {
        identityApiRateLimitService.checkOutboundGoogle(code);
        var result = authenticationService.outboundAuthenticate(code);
        setAccessTokenCookie(response, result.getToken());
        setRefreshTokenCookie(response, result.getRefreshToken());
        return ApiResponse.<Void>builder()
                .message("Login success!")
                .build();
    }

    private String getAccessTokenFromCookie(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
