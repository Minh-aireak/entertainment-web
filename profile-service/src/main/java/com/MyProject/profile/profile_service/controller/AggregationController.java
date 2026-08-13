package com.MyProject.profile.profile_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.profile.profile_service.dto.response.UserFullSummaryResponse;
import com.MyProject.profile.profile_service.service.AggregationService;
import com.MyProject.profile.profile_service.service.ProfileApiRateLimitService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aggregation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AggregationController {
    AggregationService aggregationService;
    ProfileApiRateLimitService profileApiRateLimitService;

    @GetMapping("/my-summary")
    public ApiResponse<UserFullSummaryResponse> getUserSummary() {
        profileApiRateLimitService.checkProfileSummary(SecurityUtils.getCurrentUserId());
        return ApiResponse.<UserFullSummaryResponse>builder()
                .result(aggregationService.getUserSummary())
                .build();
    }
}
