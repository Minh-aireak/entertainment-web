package com.MyProject.profile.profile_service.service;

import com.MyProject.common_dto.event.dto.request.BulkUserProfileRequest;
import com.MyProject.common_dto.event.dto.ProfileUpdatedEvent;
import com.MyProject.common_dto.event.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.dto.request.SearchUserProfileRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.dto.response.FileResponse;
import com.MyProject.profile.profile_service.entity.UserProfile;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.mapper.UserProfileMapper;
import com.MyProject.profile.profile_service.repository.UserProfileRepository;
import com.MyProject.profile.profile_service.repository.httpclient.FileClient;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@FieldDefaults(level = AccessLevel.PRIVATE)
class UserProfileServiceTest {
    @Autowired
    UserProfileService userProfileService;

    @MockitoBean
    PlatformTransactionManager platformTransactionManager;

    @MockitoBean
    UserProfileMapper userProfileMapper;

    @MockitoBean
    FileClient client;

    @MockitoBean
    UserProfileRepository userProfileRepository;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    UserProfile profile;
    UserProfile profile2;
    UserProfileResponse response;
    UserProfileResponse response2;

    @BeforeEach
    void initData() {
         profile = UserProfile.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .displayName("aireak")
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .avatar("https://host/avatar/u1.png")
                .build();

         profile2 = UserProfile.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob(LocalDate.parse("18/12/2005",
                      DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .phoneNumber("0865788560")
                .avatar("https://host/avatar/u1.png")
                .build();

        response = UserProfileResponse.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .displayName("aireak")
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .build();

        response2 = UserProfileResponse.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob(LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .phoneNumber("0865788560")
                .avatar("https://host/avatar/u1.png")
                .build();
    }

    private void mockAuthenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("aireak");

        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createProfile_success() {
        UserProfileCreationRequest request = UserProfileCreationRequest.builder()
                .userId("123456789")
                .username("aireak")
                .email("aireak@gmail.com")
                .displayName("aireak")
                .joinDate(LocalDateTime.parse("2025-12-28T19:42:15.123"))
                .build();

        when(userProfileMapper.toUserProfile(request)).thenReturn(profile);
        when(userProfileRepository.save(profile)).thenReturn(profile);
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);

        var result = userProfileService.createProfile(request);

        assertThat(result).usingRecursiveComparison().isEqualTo(response);

        verify(userProfileMapper, times(1)).toUserProfile(any());
        verify(userProfileRepository, times(1)).save(any());
        verify(userProfileMapper, times(1)).toUserProfileResponse(any());
    }

    @Test
    void updateProfile_success() {
        mockAuthenticatedUser();
        UserProfileUpdateRequest updateRequest = UserProfileUpdateRequest.builder()
                .email("aireakUpdate@gmail.com")
                .displayName("aireakUpdate")
                .lastName("aireakUpdate")
                .dob(LocalDate.parse("18/12/2005",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .phoneNumber("0865788560")
                .build();

        when(userProfileRepository.findByUsername("aireak")).thenReturn(profile);

        when(userProfileRepository.save(profile)).thenReturn(profile2);
        when(userProfileMapper.toUserProfileResponse(profile2)).thenReturn(response2);

        var result = userProfileService.updateProfile(updateRequest);

        assertThat(result).usingRecursiveComparison().isEqualTo(response2);

        verify(userProfileRepository, times(1)).findByUsername(any());
        verify(userProfileMapper, times(1)).update(any(), any());
        verify(userProfileRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(any(), any());
        verify(userProfileMapper, times(1)).toUserProfileResponse(any());
    }

    @Test
    void updateProfile_unAuthenticated() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        var exception = assertThrows(AppException.class,
                () -> userProfileService.updateProfile(request));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        verify(userProfileRepository, never()).findByUsername(any());
        verify(userProfileMapper, never()).update(any(), any());
        verify(userProfileRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
        verify(userProfileMapper, never()).toUserProfileResponse(any());
    }

    @Test
    void getMyInfo_success() {
        mockAuthenticatedUser();

        when(userProfileRepository.findByUsername("aireak")).thenReturn(profile);
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);

        var result = userProfileService.getMyInfo();

        assertThat(result).usingRecursiveComparison().isEqualTo(response);

        verify(userProfileRepository, times(1)).findByUsername(any());
        verify(userProfileMapper, times(1)).toUserProfileResponse(any());
    }

    @Test
    void getMyInfo_unAuthenticated() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> userProfileService.getMyInfo());

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        verify(userProfileRepository, never()).findByUsername(any());
        verify(userProfileMapper, never()).toUserProfileResponse(any());
    }

    @Test
    void getAllProfiles_success() {
        when(userProfileRepository.findAll()).thenReturn(List.of(profile));
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);

        var result = userProfileService.getAllProfiles();

        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(response));

        verify(userProfileRepository, times(1)).findAll();
        verify(userProfileMapper).toUserProfileResponse(any());
    }

    @Test
    void getProfile_success() {
        when(userProfileRepository.findById("123456789")).thenReturn(Optional.of(profile));
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);

        var result = userProfileService.getProfile("123456789");

        assertThat(result).usingRecursiveComparison().isEqualTo(response);

        verify(userProfileRepository, times(1)).findById(any());
        verify(userProfileMapper, times(1)).toUserProfileResponse(any());
    }

    @Test
    void getProfile_profileNotFound() {
        when(userProfileRepository.findById("123456789")).thenReturn(Optional.empty());
        var exception = assertThrows(AppException.class, () -> userProfileService.getProfile("123456789"));

        assertEquals(ErrorCode.PROFILE_NOT_FOUND, exception.getErrorCode());

        verify(userProfileRepository, times(1)).findById(any());
        verify(userProfileMapper, never()).toUserProfileResponse(any());
    }

    @Test
    void getBulkProfiles_success() {
        BulkUserProfileRequest req = BulkUserProfileRequest.builder()
                .userIds(List.of("123456789"))
                .build();

        when(userProfileRepository.findAllById(req.getUserIds())).thenReturn(List.of(profile));
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);

        var result = userProfileService.getBulkProfiles(req);

        assertThat(result).usingRecursiveComparison().isEqualTo(List.of(response));

        verify(userProfileRepository, times(1)).findAllById(any());
        verify(userProfileMapper).toUserProfileResponse(any());
    }

    @Test
    void updateAvatar_success() {
        mockAuthenticatedUser();
        MockMultipartFile multipartFile = new MockMultipartFile("file", "u1.png",
                "image/png", "data".getBytes());

        FileResponse fileResponse = new FileResponse("https://host/avatar/u1.png");

        when(userProfileRepository.findByUsername(any())).thenReturn(profile);
        when(client.uploadAvatar(any())).thenReturn(ApiResponse.<FileResponse>builder()
                .result(fileResponse).build());
        when(userProfileRepository.save(any())).thenReturn(profile);
        when(userProfileMapper.toUserProfileResponse(any())).thenReturn(response);

        var result = userProfileService.updateAvatar(multipartFile);

        assertThat(result).usingRecursiveComparison().isEqualTo(response);

        verify(userProfileRepository, times(1)).findByUsername(any());
        verify(client, times(1)).uploadAvatar(any());
        verify(userProfileRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(any(), any(ProfileUpdatedEvent.class));
        verify(userProfileMapper, times(1)).toUserProfileResponse(any());
    }

    @Test
    void updateAvatar_unAuthenticated(){
        MockMultipartFile multipartFile = new MockMultipartFile("file", "u1.png",
                "image/png", "data".getBytes());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);

        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> userProfileService.updateAvatar(multipartFile));

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());

        verify(userProfileRepository, never()).findByUsername(any());
        verify(client, never()).uploadAvatar(any());
        verify(userProfileRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(ProfileUpdatedEvent.class));
        verify(userProfileMapper, never()).toUserProfileResponse(any());
    }

    @Test
    void searchProfile_success() {
        SearchUserProfileRequest request = new SearchUserProfileRequest("aireak", 1, 5);

        List<UserProfile> profiles = List.of(profile);
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), Sort.by("joinDate").ascending());
        Page<UserProfile> pageData = new PageImpl<>(profiles, pageable, 1);

        when(userProfileRepository.findAllByDisplayName(request.getDisplayName(), pageable)).thenReturn(pageData);
        when(userProfileMapper.toUserProfileResponse(profile)).thenReturn(response);
        var result = userProfileService.searchProfile(request);

        assertNotNull(result);
        assertEquals(1, result.getCurrentPage());
        assertEquals(1, result.getTotalElement());
        assertEquals(1, result.getData().size());
        assertEquals("aireak", result.getData().getFirst().getDisplayName());

        verify(userProfileRepository).findAllByDisplayName(any(), any());
        verify(userProfileMapper).toUserProfileResponse(any());
    }
}
