package com.MyProject.profile.profile_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.profile.profile_service.dto.response.UserFullSummaryResponse;
import com.MyProject.profile.profile_service.repository.httpClient.FriendServiceClient;
import com.MyProject.profile.profile_service.repository.httpClient.PostServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * getUserSummary() calls getPostCount()/getFriendCount() via `this::` method references, which
 * bypasses the Spring AOP proxy backing @CircuitBreaker/@TimeLimiter (self-invocation) - their
 * fallback methods never actually run from inside getUserSummary(). A downstream failure surfaces
 * as a raw CompletionException instead of degrading gracefully; see the empty-data-user 500s noted
 * in project history.
 */
@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    @Mock PostServiceClient postServiceClient;
    @Mock FriendServiceClient friendServiceClient;
    @Mock UserProfileService userProfileService;

    AggregationService aggregationService;

    @BeforeEach
    void setUp() {
        aggregationService = new AggregationService(postServiceClient, friendServiceClient,
                userProfileService, Runnable::run);
    }

    private UserProfileResponse profile() {
        return UserProfileResponse.builder()
                .userId("user-1").username("aireak").email("aireak@gmail.com")
                .displayName("Aireak").dob(LocalDate.of(2000, 1, 1))
                .joinDate(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    @Test
    void getUserSummary_happyPath_combinesProfileWithCounts() {
        when(userProfileService.getMyProfile()).thenReturn(profile());
        when(postServiceClient.countMyPosts()).thenReturn(ApiResponse.<Integer>builder().result(5).build());
        when(friendServiceClient.countMyFriends()).thenReturn(ApiResponse.<Integer>builder().result(3).build());

        UserFullSummaryResponse response = aggregationService.getUserSummary();

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getTotalPosts()).isEqualTo(5);
        assertThat(response.getTotalFriends()).isEqualTo(3);
        assertThat(response.getDob()).isEqualTo("2000-01-01");
    }

    @Test
    void getUserSummary_newUserWithNoPostsOrFriends_returnsZeroCounts() {
        when(userProfileService.getMyProfile()).thenReturn(profile());
        when(postServiceClient.countMyPosts()).thenReturn(ApiResponse.<Integer>builder().result(0).build());
        when(friendServiceClient.countMyFriends()).thenReturn(ApiResponse.<Integer>builder().result(0).build());

        UserFullSummaryResponse response = aggregationService.getUserSummary();

        assertThat(response.getTotalPosts()).isZero();
        assertThat(response.getTotalFriends()).isZero();
    }

    @Test
    void getUserSummary_nullProfileFields_mapToNullStringsWithoutThrowing() {
        UserProfileResponse profile = UserProfileResponse.builder().userId("user-1").build();
        when(userProfileService.getMyProfile()).thenReturn(profile);
        when(postServiceClient.countMyPosts()).thenReturn(ApiResponse.<Integer>builder().result(0).build());
        when(friendServiceClient.countMyFriends()).thenReturn(ApiResponse.<Integer>builder().result(0).build());

        UserFullSummaryResponse response = aggregationService.getUserSummary();

        assertThat(response.getDob()).isNull();
        assertThat(response.getJoinDate()).isNull();
    }

    @Test
    void getUserSummary_postServiceThrows_propagatesAsCompletionExceptionInsteadOfUsingFallback() {
        when(postServiceClient.countMyPosts()).thenThrow(new RuntimeException("post-service down"));

        assertThatThrownBy(() -> aggregationService.getUserSummary())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void getPostCount_delegatesToClientAndUnwrapsResult() {
        when(postServiceClient.countMyPosts()).thenReturn(ApiResponse.<Integer>builder().result(42).build());

        assertThat(aggregationService.getPostCount()).isEqualTo(42);
    }

    @Test
    void getFriendCount_delegatesToClientAndUnwrapsResult() {
        when(friendServiceClient.countMyFriends()).thenReturn(ApiResponse.<Integer>builder().result(7).build());

        assertThat(aggregationService.getFriendCount()).isEqualTo(7);
    }

    @Test
    void postCountFallback_returnsNull() {
        assertThat(aggregationService.postCountFallback(new RuntimeException("boom"))).isNull();
    }

    @Test
    void friendCountFallback_returnsNull() {
        assertThat(aggregationService.friendCountFallback("user-1", new RuntimeException("boom"))).isNull();
    }
}
