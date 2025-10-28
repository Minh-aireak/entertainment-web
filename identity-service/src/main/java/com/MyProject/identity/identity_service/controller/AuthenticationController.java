package com.MyProject.identity.identity_service.controller;

import java.text.ParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.nimbusds.jose.JOSEException;
import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.request.IntrospectRequest;
import com.MyProject.identity.identity_service.dto.request.LogoutRequest;
import com.MyProject.identity.identity_service.dto.request.RefreshRequest;
import com.MyProject.identity.identity_service.dto.response.ApiResponse;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;
import com.MyProject.identity.identity_service.service.AuthenticationService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("/login")
    ApiResponse<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) throws JOSEException {
        var result = authenticationService.authentication(request);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request) throws ParseException {
        var result = authenticationService.introspectResponse(request);
        return ApiResponse.<IntrospectResponse>builder().result(result).build();
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    ApiResponse<Void> logout(@RequestBody LogoutRequest request, @AuthenticationPrincipal Jwt jwt)
            throws ParseException, JOSEException {
        authenticationService.logout(request, jwt);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    ApiResponse<AuthenticationResponse> refresh(@RequestBody RefreshRequest request, @AuthenticationPrincipal Jwt jwt)
            throws JOSEException, ParseException {
        var result = authenticationService.refreshToken(request, jwt);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping("/outbound/authentication")
    ApiResponse<AuthenticationResponse> outboundAuthenticate(@RequestParam("code") String code) throws JOSEException {
        var result = authenticationService.outboundAuthenticate(code);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }
}
