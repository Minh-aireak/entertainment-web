package com.MyProject.post.post_service.controller;

import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.post.post_service.configuration.SecurityConfig;
import com.MyProject.post.post_service.dto.request.PostRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.service.PostApiRateLimitService;
import com.MyProject.post.post_service.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        PostControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostService postService;

    @MockitoBean
    PostApiRateLimitService postApiRateLimitService;

    final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Test
    void createPost_authenticated_returnsCreatedPost() throws Exception {
        when(postService.createPost(any())).thenReturn(PostResponse.builder().id("p-1").content("hello").build());
        PostRequest request = PostRequest.builder().postType("TEXT").content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("p-1"));

        verify(postApiRateLimitService).checkPostWrite("user-1");
    }

    @Test
    void createPost_unauthenticated_returns401() throws Exception {
        PostRequest request = PostRequest.builder().postType("TEXT").content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(postService);
    }

    @Test
    void createPost_serviceRejectsInvalidType_returns400() throws Exception {
        when(postService.createPost(any())).thenThrow(new AppException(ErrorCode.INVALID_POST_TYPE));
        PostRequest request = PostRequest.builder().postType("BAD").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.INVALID_POST_TYPE.getCode()));
    }

    @Test
    void getMyPost_notFound_returns404() throws Exception {
        when(postService.getMyPost("missing")).thenThrow(new AppException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/missing").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.POST_NOT_FOUND.getCode()));
    }

    @Test
    void toggleLike_authenticated_returnsLikeState() throws Exception {
        when(postService.toggleLike("p-1")).thenReturn(LikeResponse.builder().liked(true).likeCount(1).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/p-1/like").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.liked").value(true))
                .andExpect(jsonPath("result.likeCount").value(1));
    }

    @Test
    void deletePost_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/p-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(postService).deletePost("p-1");
    }

    @Test
    void getRandomPosts_defaultParams_delegatesWithEmptyExcludeList() throws Exception {
        when(postService.getRandomPosts(5, java.util.List.of())).thenReturn(java.util.List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/random").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(postService).getRandomPosts(5, java.util.List.of());
    }

    @Test
    void getRandomPosts_withExcludeIds_parsesCommaSeparatedList() throws Exception {
        when(postService.getRandomPosts(5, java.util.List.of("a", "b"))).thenReturn(java.util.List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/random").param("excludeIds", "a, b")
                        .with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(postService).getRandomPosts(5, java.util.List.of("a", "b"));
    }
}
