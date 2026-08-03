package com.MyProject.identity.identity_service.controller;

import com.MyProject.identity.identity_service.service.IdentityApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;

import java.util.List;

import com.MyProject.common.dto.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.RoleCreationRequest;
import com.MyProject.identity.identity_service.dto.request.RoleUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.service.RoleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleController {
    RoleService roleService;
    IdentityApiRateLimitService identityApiRateLimitService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<RoleResponse> createRole(@RequestBody RoleCreationRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkRoleManagement(userId);
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.createRole(request))
                .message("Create role success!")
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<List<RoleResponse>> getAllRoles() {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkRoleManagement(userId);
        return ApiResponse.<List<RoleResponse>>builder()
                .result(roleService.getAllRoles())
                .build();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<RoleResponse> updateRole(@RequestBody RoleUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkRoleManagement(userId);
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.updateRole(request))
                .message("Update role success!")
                .build();
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")

    ApiResponse<Void> deleteRole(@PathVariable String name) {
        String userId = SecurityUtils.getCurrentUserId();
        identityApiRateLimitService.checkRoleManagement(userId);
        roleService.deleteRole(name);
        return ApiResponse.<Void>builder()
                .message("Deleted role success!")
                .build();
    }
}
