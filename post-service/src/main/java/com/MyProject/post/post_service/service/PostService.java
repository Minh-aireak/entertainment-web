package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.dto.request.PostRequest;
import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
    PostRepository postRepository;
    PostMapper postMapper;

    public PostResponse createPost(PostRequest request){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Post post = Post.builder()
                .userId(jwt.getClaim("userId"))
                .content(request.getContent())
                .createdDate(Instant.now())
                .modifiedDate(Instant.now())
                .build();

        return postMapper.toPostResponse(postRepository.save(post));
    }

    public List<PostResponse> getMyPosts(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        return (List<PostResponse>) postRepository.findAllByUserId(jwt.getClaim("userId"))
                .stream().map(postMapper::toPostResponse).toList();
    }
}
