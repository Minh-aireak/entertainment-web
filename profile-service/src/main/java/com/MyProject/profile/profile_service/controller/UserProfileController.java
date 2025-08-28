package com.MyProject.profile.profile_service.controller;

import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.service.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {
    UserProfileService userProfileService;

    @PostMapping("/internal/registration")
    ApiResponse<UserProfileResponse> createProfile(@RequestBody UserProfileCreationRequest request){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.createProfile(request))
                .build();
    }

    @PostMapping("/update-my-profile")
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

    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<UserProfileResponse> getProfile(@PathVariable String profileId){
        return ApiResponse.<UserProfileResponse>builder()
                .result(userProfileService.getProfile(profileId))
                .build();
    }
}
