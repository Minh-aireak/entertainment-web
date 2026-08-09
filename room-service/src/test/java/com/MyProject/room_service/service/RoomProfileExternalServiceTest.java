package com.MyProject.room_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.repository.httpclient.ProfileClient;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomProfileExternalServiceTest {

    @Mock ProfileClient profileClient;

    RoomProfileExternalService roomProfileExternalService;

    @BeforeEach
    void setUp() {
        roomProfileExternalService = new RoomProfileExternalService(profileClient);
    }

    @Test
    void getBulkUserProfiles_happyPath_returnsResultMap() {
        Map<String, UserProfileResponse> result = Map.of("user-1", UserProfileResponse.builder().userId("user-1").build());
        when(profileClient.getBulkUserProfiles(any())).thenReturn(ApiResponse.<Map<String, UserProfileResponse>>builder().result(result).build());

        Map<String, UserProfileResponse> response = roomProfileExternalService.getBulkUserProfiles(Set.of("user-1"));

        assertThat(response).containsKey("user-1");
    }

    @Test
    void getBulkUserProfiles_nullResult_returnsEmptyMap() {
        when(profileClient.getBulkUserProfiles(any())).thenReturn(ApiResponse.<Map<String, UserProfileResponse>>builder().result(null).build());

        Map<String, UserProfileResponse> response = roomProfileExternalService.getBulkUserProfiles(Set.of("user-1"));

        assertThat(response).isEmpty();
    }

    @Test
    void getBulkUserProfilesFallback_rateLimited_throwsRateLimitExceeded() {
        assertThatThrownBy(() -> roomProfileExternalService.getBulkUserProfilesFallback(
                Set.of("user-1"), org.mockito.Mockito.mock(RequestNotPermitted.class)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void getBulkUserProfilesFallback_profileServiceDown_returnsEmptyMapInsteadOfThrowing() {
        Map<String, UserProfileResponse> response = roomProfileExternalService.getBulkUserProfilesFallback(
                Set.of("user-1"), new RuntimeException("profile-service down"));

        assertThat(response).isEmpty();
    }
}
