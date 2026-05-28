package com.MyProject.identity.identity_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.identity.identity_service.dto.request.ForgotPasswordRequest;
import com.MyProject.identity.identity_service.dto.request.ResetPasswordRequest;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.UserCreationRequest;
import com.MyProject.identity.identity_service.dto.request.ChangePasswordRequest;
import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    @PostMapping("/registration")
    ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .message("Register account success!")
                .build();
    }

    @PutMapping("/password")
    ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<Void>builder()
                .message("Change password success!")
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserResponse>>builder()
                .result(userService.getUsers(page, size))
                .build();
    }

    @PutMapping("/{id}/toggle-account")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<Void> toggleAccount(@PathVariable String id) {
        return ApiResponse.<Void>builder()
                .message(userService.toggleAccount(id))
                .build();
    }

    @PostMapping("/forgot-password")
    ApiResponse<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return ApiResponse.<String>builder()
                .result(userService.forgotPassword(request))
                .build();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ApiResponse.<Void>builder()
                .message("Reset password success!")
                .build();
    }
}
