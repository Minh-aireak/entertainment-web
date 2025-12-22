package com.MyProject.profile.profile_service.controller;

import com.MyProject.common_dto.event.dto.UserProfileResponse;
import com.MyProject.profile.profile_service.dto.request.BulkUserProfileRequest;
import com.MyProject.profile.profile_service.dto.request.SearchUserProfileRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.dto.response.PageResponse;
import com.MyProject.profile.profile_service.service.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {
    UserProfileService userProfileService;

    @PutMapping("/update-my-profile")
    ApiResponse<UserProfileResponse> updateProfile(@RequestBody UserProfileUpdateRequest request){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.updateProfile(request))
                .build();
    }

    @GetMapping("/get-my-info")
    ApiResponse<UserProfileResponse> getMyInfo(){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.getMyInfo())
                .build();
    }

    @GetMapping("/read")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<List<UserProfileResponse>> getAllProfiles(){
        return ApiResponse.<List<UserProfileResponse>>builder()
                .result(userProfileService.getAllProfiles())
                .build();
    }

    @PostMapping("/internal/registration")
    ApiResponse<UserProfileResponse> createProfile(@RequestBody UserProfileCreationRequest request){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.createProfile(request))
                .build();
    }

    @GetMapping("/internal/user-profile/{userId}")
    ApiResponse<UserProfileResponse> getProfile(@PathVariable String userId){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.getProfile(userId))
                .build();
    }

    @PostMapping("/internal/bulk-user-profiles")
    ApiResponse<List<UserProfileResponse>> getBulkProfiles(@RequestBody BulkUserProfileRequest request){
        return ApiResponse.<List<UserProfileResponse>>builder()
                .result(userProfileService.getBulkProfiles(request))
                .build();
    }

    @PutMapping("/upload-avatar")
    ApiResponse<UserProfileResponse> updateAvatar(@RequestParam("file") MultipartFile multipartFile){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.updateAvatar(multipartFile))
                .build();
    }

    @PostMapping("/search")
    ApiResponse<PageResponse<UserProfileResponse>> searchProfile(@RequestBody SearchUserProfileRequest request){
        return ApiResponse.<PageResponse<UserProfileResponse>>builder()
                .result(userProfileService.searchProfile(request))
                .build();
    }
}
