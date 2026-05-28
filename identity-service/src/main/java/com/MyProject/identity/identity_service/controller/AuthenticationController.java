package com.MyProject.identity.identity_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.AuthenticationRequest;
import com.MyProject.identity.identity_service.dto.response.AuthenticationResponse;
import com.MyProject.identity.identity_service.service.AuthenticationService;
import com.MyProject.identity.identity_service.dto.response.IntrospectResponse;

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
    ApiResponse<AuthenticationResponse> authenticate(@RequestBody @Valid AuthenticationRequest request) {
        var result = authenticationService.authentication(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Login success!")
                .build();
    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestParam("token") String token) {
        var result = authenticationService.introspectResponse(token);
        return ApiResponse.<IntrospectResponse>builder()
                .result(result)
                .build();
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    ApiResponse<Void> logout(@RequestParam("token") String token) {
        authenticationService.logout(token);
        return ApiResponse.<Void>builder()
                .message("Logout success!")
                .build();
    }

    @PostMapping("/refresh-token")
    @PreAuthorize("isAuthenticated()")
    ApiResponse<AuthenticationResponse> refresh(@RequestParam("token") String token) {
        var result = authenticationService.refreshToken(token);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping("/outbound/google")
    ApiResponse<AuthenticationResponse> outboundAuthenticate(@RequestParam("code") String code) {
        var result = authenticationService.outboundAuthenticate(code);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Login success!")
                .build();
    }
}
