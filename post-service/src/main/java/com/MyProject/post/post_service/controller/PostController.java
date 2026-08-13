package com.MyProject.post.post_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.request.PostRequest;
import com.MyProject.post.post_service.dto.request.PostUpdateRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.PostResponse;
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
    ApiResponse<PostResponse> createPost(@RequestBody PostRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostWrite(userId);
        return ApiResponse.<PostResponse>builder()
                .result(postService.createPost(request))
                .message("Created success!")
                .build();
    }

    @GetMapping("/search")
    @CircuitBreaker(name = "postSearchApi")
    ApiResponse<PageResponse<PostResponse>> searchPosts(@RequestParam String query,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostSearch(userId);
        return ApiResponse.<PageResponse<PostResponse>>builder()
                .result(postService.searchPosts(query, page, size))
                .build();
    }

    @GetMapping
    ApiResponse<PageResponse<PostResponse>> getMyPosts(@RequestParam(value = "page", defaultValue = "1") int page,
                                                         @RequestParam(value = "size", defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRead(userId);
        return ApiResponse.<PageResponse<PostResponse>>builder()
                .result(postService.getMyPosts(page, size))
                .build();
    }

    @GetMapping("/{id}")
    ApiResponse<PostResponse> getMyPost(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRead(userId);
        return ApiResponse.<PostResponse>builder()
                .result(postService.getMyPost(id))
                .build();
    }

    @PutMapping("/{id}")
    ApiResponse<PostResponse> updatePost(@PathVariable String id, @RequestBody PostUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostUpdate(userId);
        return ApiResponse.<PostResponse>builder()
                .result(postService.updatePost(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    ApiResponse<Void> deletePost(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostDelete(userId);
        postService.deletePost(id);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PostMapping("/{id}/like")
    ApiResponse<LikeResponse> toggleLike(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostUpdate(userId);
        return ApiResponse.<LikeResponse>builder()
                .result(postService.toggleLike(id))
                .build();
    }

    @GetMapping("/random")
    ApiResponse<List<PostResponse>> getRandomPosts(@RequestParam(value = "limit", defaultValue = "5") int limit,
                                                     @RequestParam(value = "excludeIds", required = false, defaultValue = "") String excludeIds) {
        String userId = SecurityUtils.getCurrentUserId();
        postApiRateLimitService.checkPostRandom(userId);

        List<String> excludeIdList = excludeIds.isBlank()
                ? List.of()
                : Arrays.stream(excludeIds.split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .toList();

        return ApiResponse.<List<PostResponse>>builder()
                .result(postService.getRandomPosts(limit, excludeIdList))
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

    @GetMapping("/internal/{id}/owner")
    ApiResponse<String> getPostOwner(@PathVariable String id) {
        return ApiResponse.<String>builder()
                .result(postService.getPostOwner(id))
                .build();
    }
}
