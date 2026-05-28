package com.MyProject.profile.profile_service.controller;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.configuration.CustomJwtDecoder;
import com.MyProject.profile.profile_service.configuration.JwtAuthenticationEntryPoint;
import com.MyProject.profile.profile_service.configuration.SecurityConfig;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.service.UserProfileService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import(
        {SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class}
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserProfileService userProfileService;

    ObjectMapper objectMapper;
    UserProfileResponse response;

    @BeforeEach
    void initData() {
        objectMapper = new ObjectMapper();

        response = UserProfileResponse.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .displayName("aireak")
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .build();
    }

    @Test
    void createProfile_success() throws Exception {
        UserProfileCreationRequest request = UserProfileCreationRequest.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .displayName("aireak")
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .build();
        String content = objectMapper.writeValueAsString(request);

        when(userProfileService.createProfile(any(UserProfileCreationRequest.class))).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.userId").value("123456789"))
                .andExpect(jsonPath("result.username").value("aireak"))
                .andExpect(jsonPath("result.email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result.displayName").value("aireak"))
                .andExpect(jsonPath("result.joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).createProfile(any(UserProfileCreationRequest.class));
    }

    @Test
    @WithMockUser
    void updateProfile_success() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob( LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        when(userProfileService.updateProfile(updateRequest)).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.userId").value("123456789"))
                .andExpect(jsonPath("result.username").value("aireak"))
                .andExpect(jsonPath("result.email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result.displayName").value("aireak"))
                .andExpect(jsonPath("result.joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    void updateProfile_unAuthenticated() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdategmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob( LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        when(userProfileService.updateProfile(updateRequest)).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8101))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(userProfileService, never()).updateProfile(any());
    }

    @Test
    @WithMockUser
    void updateProfile_emailInvalid() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdategmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob( LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(8110))
                .andExpect(jsonPath("message").value("Email invalid!"));

        verify(userProfileService, never()).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    @WithMockUser
    void updateProfile_displayNameEmpty() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("")
                .lastName("aireakUpdate")
                .dob( LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(8108))
                .andExpect(jsonPath("message").value("Display name cannot be blank!"));

        verify(userProfileService, never()).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    @WithMockUser
    void updateProfile_lastNameEmpty() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("")
                .dob(LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(8103))
                .andExpect(jsonPath("message").value("Name cannot be blank!"));

        verify(userProfileService, never()).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    @WithMockUser
    void updateProfile_invalidDob() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob(LocalDate.parse("18/12/2024",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(8105))
                .andExpect(jsonPath("message").value("You must be at least 16 years old!"));

        verify(userProfileService, never()).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    @WithMockUser
    void updateProfile_invalidPhoneNumber() throws Exception {
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob(LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("086578856")
                .build();

        String content = objectMapper.writeValueAsString(updateRequest);

        mockMvc.perform(MockMvcRequestBuilders.put("/my-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(8109))
                .andExpect(jsonPath("message").value("Phone number invalid!"));

        verify(userProfileService, never()).updateProfile(any(UserProfileUpdateRequest.class));
    }

    @Test
    @WithMockUser
    void getMyInfo_success() throws Exception {
        when(userProfileService.getMyInfo()).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.get("/my-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.userId").value("123456789"))
                .andExpect(jsonPath("result.username").value("aireak"))
                .andExpect(jsonPath("result.email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result.displayName").value("aireak"))
                .andExpect(jsonPath("result.joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).getMyInfo();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllProfiles_success() throws Exception {
        when(userProfileService.getAllProfiles()).thenReturn(List.of(response));

        mockMvc.perform(MockMvcRequestBuilders.get(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result", hasSize(1)))
                .andExpect(jsonPath("result[0].userId").value("123456789"))
                .andExpect(jsonPath("result[0].username").value("aireak"))
                .andExpect(jsonPath("result[0].email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result[0].displayName").value("aireak"))
                .andExpect(jsonPath("result[0].joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).getAllProfiles();
    }

    @Test
    @WithMockUser(roles = "OtherRoles")
    void getAllProfiles_otherRoles() throws Exception {
        when(userProfileService.getAllProfiles()).thenReturn(List.of(response));

        mockMvc.perform(MockMvcRequestBuilders.get(""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(8102))
                .andExpect(jsonPath("message").value("You don't have permission!"));

        verify(userProfileService, never()).getAllProfiles();
    }

    @Test
    void getAllProfiles_unAuthenticated() throws Exception {
        when(userProfileService.getAllProfiles()).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(MockMvcRequestBuilders.get(""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8101))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(userProfileService, never()).getAllProfiles();
    }

    @Test
    @WithMockUser
    void getProfile_byId_success() throws Exception {
        when(userProfileService.getProfile("123456789")).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/user-profile/123456789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.userId").value("123456789"))
                .andExpect(jsonPath("result.username").value("aireak"))
                .andExpect(jsonPath("result.email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result.displayName").value("aireak"))
                .andExpect(jsonPath("result.joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).getProfile(anyString());
    }

    @Test
    void getProfile_byId_unAuthenticated() throws Exception {
        when(userProfileService.getProfile("123456789")).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(MockMvcRequestBuilders.get(""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8101))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(userProfileService, never()).getProfile(any());
    }

    @Test
    @WithMockUser
    void getProfile_byId_profileNotFound() throws Exception {
        when(userProfileService.getProfile("123456789")).thenThrow(new AppException(ErrorCode.PROFILE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/user-profile/123456789"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(8107))
                .andExpect(jsonPath("message").value("Profile not existed!"));

        verify(userProfileService, times(1)).getProfile(anyString());
    }

    @Test
    @WithMockUser
    void bulkProfiles_success() throws Exception {
        BulkUserProfileRequest req = BulkUserProfileRequest.builder()
                .userIds(Set.of("123456789"))
                .build();

        when(userProfileService.getBulkProfiles(any(BulkUserProfileRequest.class))).thenReturn(Map.of("123456789", response));

        String content = objectMapper.writeValueAsString(req);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/bulk-user-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result['123456789'].userId").value("123456789"))
                .andExpect(jsonPath("result['123456789'].username").value("aireak"))
                .andExpect(jsonPath("result['123456789'].email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result['123456789'].displayName").value("aireak"))
                .andExpect(jsonPath("result['123456789'].joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).getBulkProfiles(any(BulkUserProfileRequest.class));
    }

    @Test
    void bulkProfiles_unAuthenticated() throws Exception {
        BulkUserProfileRequest req = BulkUserProfileRequest.builder()
                .userIds(List.of("123456789"))
                .build();

        String content = objectMapper.writeValueAsString(req);

        when(userProfileService.getBulkProfiles(any(BulkUserProfileRequest.class))).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/bulk-user-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8101))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(userProfileService, never()).getAllProfiles();
    }

    @Test
    @WithMockUser
    void uploadAvatar_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png",
                MediaType.IMAGE_PNG_VALUE, "dummy-image-bytes".getBytes());

        response.setAvatar("https://host/avatar/u1.png");

        when(userProfileService.updateAvatar(any())).thenReturn(response);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.avatar").value("https://host/avatar/u1.png"));

        verify(userProfileService, times(1)).updateAvatar(any());
    }

    @Test
    @WithMockUser
    void searchProfile_success() throws Exception {
        SearchUserProfileRequest req = new SearchUserProfileRequest("aireak", 1, 10);

        PageResponse<UserProfileResponse> pageResp = PageResponse.<UserProfileResponse>builder()
                .currentPage(1)
                .pageSize(10)
                .totalPages(1)
                .totalElement(1L)
                .data(List.of(response))
                .build();

        when(userProfileService.searchProfile(any(SearchUserProfileRequest.class))).thenReturn(pageResp);

        String content = objectMapper.writeValueAsString(req);

        mockMvc.perform(MockMvcRequestBuilders.post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.data", hasSize(1)))
                .andExpect(jsonPath("result.data[0].userId").value("123456789"))
                .andExpect(jsonPath("result.data[0].username").value("aireak"))
                .andExpect(jsonPath("result.data[0].email").value("aireak@gmail.com"))
                .andExpect(jsonPath("result.data[0].displayName").value("aireak"))
                .andExpect(jsonPath("result.data[0].joinDate").value("2025-12-28T19:42:15.123"));

        verify(userProfileService, times(1)).searchProfile(any(SearchUserProfileRequest.class));
    }

    @Test
    void searchProfile_unAuthenticated() throws Exception {
        SearchUserProfileRequest req = new SearchUserProfileRequest("aireak", 1, 10);

        String content = objectMapper.writeValueAsString(req);

        when(userProfileService.searchProfile(any(SearchUserProfileRequest.class))).thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(MockMvcRequestBuilders.post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8101))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(userProfileService, never()).searchProfile(any());
    }
}
