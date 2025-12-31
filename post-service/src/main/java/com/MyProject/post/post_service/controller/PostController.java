package com.MyProject.post.post_service.controller;

import com.MyProject.common_dto.event.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.request.ScheduleUpdateRequest;
import com.MyProject.post.post_service.dto.response.ApiResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.dto.response.StatusResponse;
import com.MyProject.post.post_service.service.PostService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostController {
    PostService postService;

    @PostMapping("/create")
    ApiResponse<ScheduleResponse> createPost(@RequestBody ScheduleRequest request){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.createPost(request))
                .message("Created success!")
                .build();
    }

    @GetMapping("/get-my-posts")
    ApiResponse<PageResponse<ScheduleResponse>> getMyPosts(
            @RequestParam(value = "page") Integer page,
            @RequestParam(value = "size") Integer size,
            @RequestParam(value = "type") String type){
        return ApiResponse.<PageResponse<ScheduleResponse>>builder()
                .result(postService.getMyPosts(page, size, type))
                .build();
    }

    @GetMapping("/get-post/{id}")
    ApiResponse<ScheduleResponse> getMyPost(@PathVariable String id,
                                            @RequestParam(value = "type") String type){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.getMyPost(id, type))
                .build();
    }

    @PutMapping("/update-post/{id}")
    ApiResponse<ScheduleResponse> updatePost(@PathVariable String id,
                                             @RequestParam(value = "type") String type,
                                             @RequestBody @Valid ScheduleUpdateRequest request){
        return ApiResponse.<ScheduleResponse>builder()
                .result(postService.updatePost(id, type, request))
                .message("Updated success!")
                .build();
    }

    @DeleteMapping("/delete/{id}")
    ApiResponse<Void> deletePost(@PathVariable String id, @RequestParam(value = "type") String type){
        postService.deletePost(id, type);
        return ApiResponse.<Void>builder()
                .message("Deleted success!")
                .build();
    }

    @GetMapping("status")
    ApiResponse<StatusResponse> getStatus(){
        return ApiResponse.<StatusResponse>builder()
                .result(postService.getStatus())
                .build();
    }
}
