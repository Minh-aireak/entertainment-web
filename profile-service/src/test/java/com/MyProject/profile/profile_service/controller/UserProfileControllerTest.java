package com.MyProject.profile.profile_service.controller;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.request.ProfileSuggestionRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.profile.profile_service.configuration.SecurityConfig;
import com.MyProject.profile.profile_service.dto.request.UpdateAvatarRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.repository.elasticsearch.UserProfileElasticRepository;
import com.MyProject.profile.profile_service.repository.mongo.OutboxRepository;
import com.MyProject.profile.profile_service.repository.mongo.UserProfileRepository;
import com.MyProject.profile.profile_service.service.ProfileApiRateLimitService;
import com.MyProject.profile.profile_service.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller now derives the caller from a JWT (SecurityUtils.getCurrentUserId(), used for
 * ProfileApiRateLimitService) for every non-public route, so @WithMockUser alone is only good
 * enough for tests that never reach the method body (validation failures) - anything that
 * exercises the real service call needs a genuine JwtAuthenticationToken via the jwt() post-processor.
 */
@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        UserProfileControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserProfileService userProfileService;

    @MockitoBean
    ProfileApiRateLimitService profileApiRateLimitService;

    @MockitoBean
    UserProfileRepository userProfileRepository;

    @MockitoBean
    OutboxRepository outboxRepository;

    @MockitoBean
    UserProfileElasticRepository userProfileElasticRepository;

    final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Test
    void getMyProfile_authenticated_returnsProfile() throws Exception {
        when(userProfileService.getMyProfile()).thenReturn(
                UserProfileResponse.builder().userId("user-1").displayName("Aireak").build());

        mockMvc.perform(MockMvcRequestBuilders.get("/my-profile").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.userId").value("user-1"))
                .andExpect(jsonPath("result.displayName").value("Aireak"));

        verify(profileApiRateLimitService).checkProfileRead("user-1");
    }

    @Test
    void getMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/my-profile"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void getMyProfile_notFound_returns404() throws Exception {
        when(userProfileService.getMyProfile()).thenThrow(new AppException(ErrorCode.PROFILE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/my-profile").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.PROFILE_NOT_FOUND.getCode()));
    }

    @Test
    void updateProfile_validRequest_returnsUpdatedProfile() throws Exception {
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("New Name").lastName("Nguyen").phoneNumber("0987654321").build();
        when(userProfileService.updateProfile(any())).thenReturn(
                UserProfileResponse.builder().userId("user-1").displayName("New Name").build());

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.displayName").value("New Name"));

        verify(profileApiRateLimitService).checkProfileWrite("user-1");
    }

    @Test
    @WithMockUser
    void updateProfile_blankDisplayName_returns400() throws Exception {
        UserProfileUpdateRequest request = UserProfileUpdateRequest.builder()
                .displayName("").lastName("Nguyen").phoneNumber("0987654321").build();

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.DISPLAY_NAME_NOT_BLANK.getCode()));

        verify(userProfileService, never()).updateProfile(any());
    }

    @Test
    @WithMockUser
    void updateAvatar_blankFileId_returns400() throws Exception {
        UpdateAvatarRequest request = UpdateAvatarRequest.builder().avatarFileId("").build();

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile/avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.AVATAR_NOT_BLANK.getCode()));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void updateAvatar_validFileId_delegatesToServiceWithExtractedFileId() throws Exception {
        UpdateAvatarRequest request = UpdateAvatarRequest.builder().avatarFileId("file-1").build();
        when(userProfileService.updateAvatar("file-1")).thenReturn(
                UserProfileResponse.builder().userId("user-1").avatar("https://cdn/file-1").build());

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile/avatar")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.avatar").value("https://cdn/file-1"));

        verify(profileApiRateLimitService).checkProfileWrite("user-1");
    }

    @Test
    void getProfile_notFound_returns404() throws Exception {
        when(userProfileService.getProfile("missing")).thenThrow(new AppException(ErrorCode.PROFILE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/missing").with(asUser("user-1")))
                .andExpect(status().isNotFound());

        verify(profileApiRateLimitService).checkProfileRead("user-1");
    }

    @Test
    void getAllProfiles_returnsPage() throws Exception {
        when(userProfileService.getAllProfiles(0, 10)).thenReturn(
                PageResponse.<UserProfileResponse>builder().currentPage(0).pageSize(10)
                        .data(List.of(UserProfileResponse.builder().userId("u1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/suggestions").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].userId").value("u1"));

        verify(profileApiRateLimitService).checkProfileRead("user-1");
    }

    @Test
    void searchProfile_returnsMatchingPage() throws Exception {
        when(userProfileService.searchProfile("aireak", 0, 10)).thenReturn(
                PageResponse.<UserProfileResponse>builder().currentPage(0).pageSize(10)
                        .data(List.of(UserProfileResponse.builder().userId("u1").username("aireak").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/search/aireak").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].username").value("aireak"));

        verify(profileApiRateLimitService).checkProfileSearch("user-1");
    }

    @Test
    void getBulkProfiles_internalEndpointStillRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/bulk-user-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BulkUserProfileRequest.builder().userIds(java.util.Set.of("u1")).build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getBulkProfiles_authenticated_returnsMap() throws Exception {
        when(userProfileService.getBulkProfiles(any())).thenReturn(
                Map.of("u1", UserProfileResponse.builder().userId("u1").build()));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/bulk-user-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(BulkUserProfileRequest.builder().userIds(java.util.Set.of("u1")).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.u1.userId").value("u1"));
    }

    @Test
    @WithMockUser
    void getSuggestionProfiles_authenticated_returnsPage() throws Exception {
        when(userProfileService.getSuggestionProfiles(any())).thenReturn(
                PageResponse.<UserProfileResponse>builder().currentPage(0).pageSize(10).data(List.of()).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ProfileSuggestionRequest.builder().page(0).size(10).build())))
                .andExpect(status().isOk());
    }
}
