package com.MyProject.post.post_service.controller;

import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.response.ApiResponse;
import com.MyProject.post.post_service.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.service.PostService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostController {
    PostService postService;

    @PostMapping("/create")
    ApiResponse<ScheduleResponse> postResponse(@RequestBody ScheduleRequest request){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.createPost(request))
                .message("Created success!")
                .build();
    }

    @PostMapping("/get-post/{postId}")
    ApiResponse<ScheduleResponse> getMyPost(@PathVariable String postId){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.getMyPost(postId))
                .build();
    }

    @GetMapping("/get-my-posts")
    ApiResponse<PageResponse<ScheduleResponse>> getMyPosts(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size){
        return ApiResponse.<PageResponse<ScheduleResponse>>builder()
                .result(postService.getMyPosts(page, size))
                .build();
    }

    @PostMapping("/update-post/{postId}")
    ApiResponse<ScheduleResponse> updatePost(@PathVariable String postId, @RequestBody ScheduleRequest request){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.updatePost(postId, request))
                .message("Updated success!")
                .build();
    }

    @DeleteMapping("/delete/{postId}")
    ApiResponse<Void> updatePost(@PathVariable String postId){
        postService.deletePost(postId);
        return ApiResponse.<Void>builder()
                .message("Deleted success!")
                .build();
    }
}
