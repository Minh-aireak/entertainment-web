package com.MyProject.identity.identity_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.identity.identity_service.service.IdentityApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.PermissionCreationRequest;
import com.MyProject.identity.identity_service.dto.request.PermissionUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
import com.MyProject.identity.identity_service.service.PermissionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionController {
    PermissionService permissionService;
    IdentityApiRateLimitService identityApiRateLimitService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<PermissionResponse> createPermission(@RequestBody PermissionCreationRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkPermissionManagement(userId);
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.createPermission(request))
                .message("Create permission success!")
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<List<PermissionResponse>> getAllRoles() {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkPermissionManagement(userId);
        return ApiResponse.<List<PermissionResponse>>builder()
                .result(permissionService.getAllPermissions())
                .build();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<PermissionResponse> updatePermission(@RequestBody PermissionUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkPermissionManagement(userId);
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.updatePermission(request))
                .message("Update permission success!")
                .build();
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<Void> deletePermission(@PathVariable String name) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkPermissionManagement(userId);
        permissionService.deletePermission(name);
        return ApiResponse.<Void>builder()
                .message("Deleted permission success!")
                .build();
    }
}
