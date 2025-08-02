package com.MyProject.identity.identity_service.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.MyProject.identity.identity_service.dto.request.PermissionCreationRequest;
import com.MyProject.identity.identity_service.dto.request.PermissionUpdateRequest;
import com.MyProject.identity.identity_service.dto.response.ApiResponse;
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

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PermissionResponse> createPermission(@RequestBody PermissionCreationRequest request) {
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.createPermission(request))
                .build();
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<PermissionResponse> updateRole(
            @PathVariable String name, @RequestBody PermissionUpdateRequest request) {
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.updatePermission(name, request))
                .build();
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<Void> deletePermission(@PathVariable String name) {
        permissionService.deletePermission(name);
        return ApiResponse.<Void>builder()
                .message("Deleted permission success!")
                .build();
    }

    @GetMapping("/read")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<PermissionResponse>> getAllRoles() {
        return ApiResponse.<List<PermissionResponse>>builder()
                .result(permissionService.getAllPermissions())
                .build();
    }
}
