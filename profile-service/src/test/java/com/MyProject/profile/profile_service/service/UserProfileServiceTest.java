package com.MyProject.profile.profile_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.request.ProfileSuggestionRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.profile.profile_service.document.UserProfileDoc;
import com.MyProject.profile.profile_service.dto.request.UserProfileCreationRequest;
import com.MyProject.profile.profile_service.dto.request.UserProfileUpdateRequest;
import com.MyProject.profile.profile_service.entity.UserProfile;
import com.MyProject.profile.profile_service.exception.AppException;
import com.MyProject.profile.profile_service.exception.ErrorCode;
import com.MyProject.profile.profile_service.mapper.UserProfileMapper;
import com.MyProject.profile.profile_service.repository.elasticsearch.UserProfileElasticRepository;
import com.MyProject.profile.profile_service.repository.httpClient.FileClient;
import com.MyProject.profile.profile_service.repository.mongo.OutboxRepository;
import com.MyProject.profile.profile_service.repository.mongo.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final String USER_ID = "user-1";

    @Mock UserProfileRepository userProfileRepository;
    @Mock UserProfileElasticRepository userProfileElasticRepository;
    @Mock UserProfileMapper userProfileMapper;
    @Mock RedisService redisService;
    @Mock OutboxRepository outboxRepository;
    @Mock FileClient fileClient;

    UserProfileService userProfileService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userProfileRepository, userProfileElasticRepository,
                userProfileMapper, redisService, outboxRepository, fileClient, new ObjectMapper());

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

        lenient().when(userProfileMapper.toUserProfileResponse(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile p = inv.getArgument(0);
            return UserProfileResponse.builder().userId(p.getUserId()).username(p.getUsername())
                    .displayName(p.getDisplayName()).build();
        });
        lenient().when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    // ---------- createProfile ----------

    @Test
    void createProfile_newProfile_savesAndIndexesToElasticsearch() {
        UserProfileCreationRequest request = UserProfileCreationRequest.builder().userId(USER_ID).username("aireak").build();
        when(userProfileRepository.existsById(USER_ID)).thenReturn(false);
        UserProfile mapped = UserProfile.builder().userId(USER_ID).username("aireak").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userProfileMapper.toUserProfile(request)).thenReturn(mapped);

        UserProfileResponse response = userProfileService.createProfile(request);

        assertThat(response.getUserId()).isEqualTo(USER_ID);
        verify(userProfileElasticRepository).save(any());
        verify(redisService).setWithExpiration(eq("profile:user:" + USER_ID), any(), eq(1L), any());
    }

    @Test
    void createProfile_alreadyExists_doesNotReindexToElasticsearch() {
        UserProfileCreationRequest request = UserProfileCreationRequest.builder().userId(USER_ID).build();
        UserProfile existing = UserProfile.builder().userId(USER_ID).username("aireak").build();
        when(userProfileRepository.existsById(USER_ID)).thenReturn(true);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        userProfileService.createProfile(request);

        verifyNoInteractions(userProfileElasticRepository);
    }

    // ---------- updateProfile ----------

    @Test
    void updateProfile_notFound_throws() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateProfile(UserProfileUpdateRequest.builder().build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void updateProfile_displayNameChanged_publishesSearchAndSocketEvents() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).displayName("Old Name").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        doAnswer(inv -> {
            UserProfileUpdateRequest req = inv.getArgument(1);
            profile.setDisplayName(req.getDisplayName());
            return null;
        }).when(userProfileMapper).update(eq(profile), any());

        userProfileService.updateProfile(UserProfileUpdateRequest.builder().displayName("New Name").build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("search.sync")));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("socket.events")));
    }

    @Test
    void updateProfile_noRelevantFieldChanged_doesNotPublishEvents() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).displayName("Same Name").city("Old City").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        doAnswer(inv -> {
            profile.setCity("New City");
            return null;
        }).when(userProfileMapper).update(eq(profile), any());

        userProfileService.updateProfile(UserProfileUpdateRequest.builder().displayName("Same Name").build());

        verifyNoInteractions(outboxRepository);
    }

    // ---------- updateAvatar ----------

    @Test
    void updateAvatar_notFound_throws() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateAvatar("file-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void updateAvatar_sameFileId_shortCircuitsWithoutSavingOrPublishing() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).avatarFileId("file-1").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        userProfileService.updateAvatar("file-1");

        verify(userProfileRepository, never()).save(any());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void updateAvatar_newFileId_savesAndPublishesEvents() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).avatarFileId("old-file").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        userProfileService.updateAvatar("new-file");

        assertThat(profile.getAvatarFileId()).isEqualTo("new-file");
        verify(userProfileRepository).save(profile);
        verify(outboxRepository, times(2)).save(any());
    }

    // ---------- getMyProfile ----------

    @Test
    void getMyProfile_notFound_throws() {
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getMyProfile())
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void getMyProfile_cacheHit_returnsCachedWithoutRebuilding() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        UserProfileResponse cached = UserProfileResponse.builder().userId(USER_ID).displayName("Cached").build();
        when(redisService.get(eq("profile:user:" + USER_ID), any())).thenReturn(cached);

        UserProfileResponse response = userProfileService.getMyProfile();

        assertThat(response).isSameAs(cached);
        verify(redisService, never()).setWithExpiration(any(), any(), anyLong(), any());
    }

    @Test
    void getMyProfile_cacheMiss_buildsAndCaches() {
        UserProfile profile = UserProfile.builder().userId(USER_ID).displayName("Fresh").build();
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(redisService.get(eq("profile:user:" + USER_ID), any())).thenReturn(null);

        UserProfileResponse response = userProfileService.getMyProfile();

        assertThat(response.getDisplayName()).isEqualTo("Fresh");
        verify(redisService).setWithExpiration(eq("profile:user:" + USER_ID), any(), eq(1L), any());
    }

    // ---------- getAllProfiles(page, size) ----------

    @Test
    void getAllProfiles_excludesCurrentUser() {
        Page<UserProfile> page = new PageImpl<>(List.of(UserProfile.builder().userId("other").build()));
        when(userProfileRepository.findByUserIdNot(eq(USER_ID), any(Pageable.class))).thenReturn(page);

        PageResponse<UserProfileResponse> response = userProfileService.getAllProfiles(0, 10);

        assertThat(response.getData()).hasSize(1);
        verify(userProfileRepository).findByUserIdNot(eq(USER_ID), any(Pageable.class));
    }

    // ---------- getSuggestionProfiles ----------

    @Test
    void getSuggestionProfiles_excludesGivenUserIds() {
        Set<String> excluded = Set.of("a", "b");
        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .excludedUserIds(excluded).page(0).size(10).build();
        Page<UserProfile> page = new PageImpl<>(List.of());
        when(userProfileRepository.findByUserIdNotIn(eq(excluded), any(Pageable.class))).thenReturn(page);

        PageResponse<UserProfileResponse> response = userProfileService.getSuggestionProfiles(request);

        assertThat(response.getData()).isEmpty();
        verify(userProfileRepository).findByUserIdNotIn(eq(excluded), any(Pageable.class));
    }

    // ---------- getBulkProfiles ----------

    @Test
    void getBulkProfiles_mapsResponsesByUserId() {
        UserProfile p1 = UserProfile.builder().userId("u1").build();
        UserProfile p2 = UserProfile.builder().userId("u2").build();
        when(userProfileRepository.findAllById(Set.of("u1", "u2"))).thenReturn(List.of(p1, p2));

        var result = userProfileService.getBulkProfiles(BulkUserProfileRequest.builder().userIds(Set.of("u1", "u2")).build());

        assertThat(result).containsOnlyKeys("u1", "u2");
    }

    // ---------- getProfile ----------

    @Test
    void getProfile_notFound_throws() {
        when(redisService.get(eq("profile:user:missing"), any())).thenReturn(null);
        when(userProfileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void getProfile_cacheHit_returnsCachedWithoutDbLookup() {
        UserProfileResponse cached = UserProfileResponse.builder().userId("u1").build();
        when(redisService.get(eq("profile:user:u1"), any())).thenReturn(cached);

        UserProfileResponse response = userProfileService.getProfile("u1");

        assertThat(response).isSameAs(cached);
        verifyNoInteractions(userProfileRepository);
    }

    // ---------- searchProfile ----------

    @Test
    void searchProfile_mapsElasticDocsToResponses() {
        UserProfileDoc doc = UserProfileDoc.builder().userId("u1").username("aireak").displayName("Aireak").build();
        Page<UserProfileDoc> page = new PageImpl<>(List.of(doc));
        when(userProfileElasticRepository.searchByUsernameContaining(eq("aireak"), any(Pageable.class))).thenReturn(page);

        PageResponse<UserProfileResponse> response = userProfileService.searchProfile("aireak", 1, 10);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getUserId()).isEqualTo("u1");
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
