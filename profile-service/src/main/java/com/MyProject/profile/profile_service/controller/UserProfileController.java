package com.MyProject.profile.profile_service.controller;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {
    UserProfileService userProfileService;

    @PutMapping("/my-profile")
    ApiResponse<UserProfileResponse> updateProfile(@RequestBody @Valid UserProfileUpdateRequest request){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.updateProfile(request))
                .build();
    }

    @GetMapping("/my-profile")
    ApiResponse<UserProfileResponse> getMyProfile(){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.getMyProfile())
                .build();
    }

    @GetMapping("/suggestions")
    ApiResponse<PageResponse<UserProfileResponse>> getAllProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        return ApiResponse.<PageResponse<UserProfileResponse>>builder()
                .result(userProfileService.getAllProfiles(page, size))
                .build();
    }

    @PostMapping("/search/{displayName}")
    ApiResponse<PageResponse<UserProfileResponse>> searchProfile(
            @PathVariable("displayName") String displayName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        return ApiResponse.<PageResponse<UserProfileResponse>>builder()
                .result(userProfileService.searchProfile(displayName, page, size))
                .build();
    }

    @PostMapping("/internal/registration")
    ApiResponse<UserProfileResponse> createProfile(@RequestBody UserProfileCreationRequest request){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.createProfile(request))
                .build();
    }

    @GetMapping("/{userId}")
    ApiResponse<UserProfileResponse> getProfile(@PathVariable String userId){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.getProfile(userId))
                .build();
    }

    @PostMapping("/internal/bulk-user-profiles")
    ApiResponse<Map<String, UserProfileResponse>> getBulkProfiles(@RequestBody BulkUserProfileRequest request){
        return ApiResponse.<Map<String, UserProfileResponse>>builder()
                .result(userProfileService.getBulkProfiles(request))
                .build();
    }

}
