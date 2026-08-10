package com.MyProject.comment_service.controller;

import com.MyProject.comment_service.service.CommentService;
import com.MyProject.comment_service.service.CommentReactionService;
import com.MyProject.comment_service.service.CommentApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.comment_service.dto.request.CreateCommentRequest;
import com.MyProject.comment_service.dto.request.ReactionRequest;
import com.MyProject.comment_service.dto.request.UpdateCommentRequest;
import com.MyProject.comment_service.dto.response.CommentReactionResponse;
import com.MyProject.comment_service.dto.response.CommentResponse;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentController {
    CommentService commentService;
    CommentReactionService commentReactionService;
    CommentApiRateLimitService commentApiRateLimitService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "commentWriteApi")
    ApiResponse<CommentResponse> createComment(@RequestBody CreateCommentRequest request){
        String userId = SecurityUtils.getCurrentUserId();
        commentApiRateLimitService.checkCommentWrite(userId, request.getSourceId());
        return ApiResponse.<CommentResponse>builder()
                .result(commentService.createComment(request))
                .build();
    }

    @GetMapping
    @RateLimiter(name = "commentReadApi")
    ApiResponse<PageResponse<CommentResponse>> getComments(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "sourceId") String sourceId){
        commentApiRateLimitService.checkCommentRead(sourceId);
        return ApiResponse.<PageResponse<CommentResponse>>builder()
                .result(commentService.getComments(sourceId, page, size))
                .build();
    }

    @PutMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "commentWriteApi")
    ApiResponse<CommentResponse> updateComment(@PathVariable String commentId, @RequestBody UpdateCommentRequest request){
        String userId = SecurityUtils.getCurrentUserId();
        commentApiRateLimitService.checkCommentUpdate(userId);
        return ApiResponse.<CommentResponse>builder()
                .result(commentService.updateComment(commentId, request))
                .build();
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "commentWriteApi")
    ApiResponse<Void> deleteComment(@PathVariable String commentId){
        String userId = SecurityUtils.getCurrentUserId();
        commentApiRateLimitService.checkCommentDelete(userId);
        commentService.deleteComment(commentId);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/count")
    @RateLimiter(name = "commentReadApi")
    ApiResponse<Long> countComments(@RequestParam(value = "sourceId") String sourceId) {
        commentApiRateLimitService.checkCommentRead(sourceId);
        return ApiResponse.<Long>builder()
                .result(commentService.countAllComments(sourceId))
                .build();
    }

    @GetMapping("/{commentId}/replies")
    @RateLimiter(name = "commentReadApi")
    ApiResponse<PageResponse<CommentResponse>> getReplies(
            @PathVariable String commentId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "5") Integer size){
        return ApiResponse.<PageResponse<CommentResponse>>builder()
                .result(commentService.getReplies(commentId, page, size))
                .build();
    }

    @PostMapping("/{commentId}/reactions")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "commentReactionApi")
    ApiResponse<CommentReactionResponse> react(@PathVariable String commentId, @RequestBody ReactionRequest request){
        String userId = SecurityUtils.getCurrentUserId();
        commentApiRateLimitService.checkCommentReaction(userId);
        return ApiResponse.<CommentReactionResponse>builder()
                .result(commentReactionService.react(commentId, userId, request.getType()))
                .build();
    }
}
