package com.MyProject.post.post_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.request.ScheduleUpdateRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.dto.response.StatusResponse;
import com.MyProject.post.post_service.service.PostApiRateLimitService;
import com.MyProject.post.post_service.service.PostService;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostController {
    PostService postService;
    PostApiRateLimitService postApiRateLimitService;

    @PostMapping
    ApiResponse<ScheduleResponse> createPost(@RequestBody ScheduleRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostWrite(userId);
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.createPost(request))
                .message("Created success!")
                .build();
    }

    @GetMapping("/search")
    @CircuitBreaker(name = "postSearchApi")
    ApiResponse<PageResponse<ScheduleResponse>> searchPosts(@RequestParam String query,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostSearch(userId);
        return ApiResponse.<PageResponse<ScheduleResponse>>builder()
                .result(postService.searchPosts(query, page, size))
                .build();
    }

    @GetMapping
    ApiResponse<PageResponse<ScheduleResponse>> getMyPosts(@RequestParam(value = "page", defaultValue = "1") int page,
                                                           @RequestParam(value = "size", defaultValue = "10") int size,
                                                           @RequestParam(value = "type", defaultValue = "BUSINESS_SCHEDULE") String type) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRead(userId);
        return ApiResponse.<PageResponse<ScheduleResponse>>builder()
                .result(postService.getMyPosts(page, size, type))
                .build();
    }

    @GetMapping("/{id}/{type}")
    ApiResponse<ScheduleResponse> getMyPost(@PathVariable String id, @PathVariable String type) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRead(userId);
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.getMyPost(id, type))
                .build();
    }

    @PutMapping("/{id}/{type}")
    ApiResponse<ScheduleResponse> updatePost(@PathVariable String id, @PathVariable String type, @RequestBody ScheduleUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostUpdate(userId);
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.updatePost(id, type, request))
                .build();
    }

    @DeleteMapping("/{id}/{type}")
    ApiResponse<Void> deletePost(@PathVariable String id, @PathVariable String type) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostDelete(userId);
        postService.deletePost(id, type);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PostMapping("/{id}/{type}/like")
    ApiResponse<LikeResponse> toggleLike(@PathVariable String id, @PathVariable String type) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostUpdate(userId);
        return ApiResponse.<LikeResponse>builder()
                .result(postService.toggleLike(id, type))
                .build();
    }

    @GetMapping("/random")
    ApiResponse<List<ScheduleResponse>> getRandomPosts(@RequestParam(value = "limit", defaultValue = "5") int limit,
                                                        @RequestParam(value = "excludeIds", required = false, defaultValue = "") String excludeIds) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRandom(userId);

        List<String> excludeIdList = excludeIds.isBlank()
                ? List.of()
                : Arrays.stream(excludeIds.split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .toList();

        return ApiResponse.<List<ScheduleResponse>>builder()
                .result(postService.getRandomPosts(limit, excludeIdList))
                .build();
    }

    @GetMapping("/status")
    ApiResponse<StatusResponse> getStatus() {
        return ApiResponse.<StatusResponse>builder()
                .result(postService.getStatus())
                .build();
    }

    @GetMapping("count")
    ApiResponse<Integer> countByUserId() {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostCount(userId);
        return ApiResponse.<Integer>builder()
                .result(postService.countByUserId())
                .build();
    }
}
