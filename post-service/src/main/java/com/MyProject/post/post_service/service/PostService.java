package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.response.PageResponse;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    PostMapper postMapper;

    public ScheduleResponse createPost(ScheduleRequest request){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Post post = Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(jwt.getClaim("userId"))
                .title(request.getTitle())
                .content(request.getContent())
                .createdDate(LocalDateTime.now())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(calculateStatus(request.getStartTime(), request.getEndTime(), LocalDateTime.now()))
                .build();

        return postMapper.toScheduleResponse(postRepository.save(post));
    }

    private String calculateStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now){
        String status = "On going";
        if (start.isAfter(now)){
            status = "Up coming";
        } else if (end.isBefore(now)) {
            status = "Completed";
        }
        return status;
    }

    public PageResponse<ScheduleResponse> getMyPosts(int page, int size){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        var pageData = postRepository.findAllByUserId(jwt.getClaim("userId"), pageable);

        return PageResponse.<ScheduleResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream().map(postMapper::toScheduleResponse).toList())
                .build();
    }

    public ScheduleResponse getMyPost(String postId){
        var schedule = postRepository.findById(postId).orElseThrow(() ->
                new AppException(ErrorCode.SCHEDULE_NOT_EXISTED));

        return postMapper.toScheduleResponse(schedule);
    }

    public ScheduleResponse updatePost(String postId, ScheduleRequest request){
        var schedule = postRepository.findById(postId).orElseThrow(() ->
                new AppException(ErrorCode.SCHEDULE_NOT_EXISTED));

        schedule.setTitle(request.getTitle());
        schedule.setContent(request.getContent());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());

        return postMapper.toScheduleResponse(postRepository.save(schedule));
    }

    public void deletePost(String postId){
        postRepository.deleteById(postId);
    }
}
