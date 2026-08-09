package com.MyProject.socket_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.socket_service.dto.response.IntrospectResponse;
import com.MyProject.socket_service.repository.httpclient.IdentityClient;
import com.MyProject.socket_service.repository.httpclient.ProfileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocketDownstreamServiceTest {

    @Mock IdentityClient identityClient;
    @Mock ProfileClient profileClient;

    SocketDownstreamService socketDownstreamService;

    @BeforeEach
    void setUp() {
        socketDownstreamService = new SocketDownstreamService(identityClient, profileClient);
    }

    @Test
    void introspectAccessToken_happyPath_returnsResult() {
        when(identityClient.introspect("token-1")).thenReturn(
                ApiResponse.<IntrospectResponse>builder().result(IntrospectResponse.builder().valid(true).userId("user-1").build()).build());

        IntrospectResponse response = socketDownstreamService.introspectAccessToken("token-1");

        assertThat(response.isValid()).isTrue();
        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    void introspectAccessToken_nullResult_returnsInvalid() {
        when(identityClient.introspect("token-1")).thenReturn(ApiResponse.<IntrospectResponse>builder().result(null).build());

        IntrospectResponse response = socketDownstreamService.introspectAccessToken("token-1");

        assertThat(response.isValid()).isFalse();
    }

    @Test
    void introspectFallback_identityServiceDown_returnsInvalidInsteadOfThrowing() {
        IntrospectResponse response = socketDownstreamService.introspectFallback("token-1", new RuntimeException("identity-service down"));

        assertThat(response.isValid()).isFalse();
    }

    @Test
    void getBulkUserProfiles_happyPath_returnsResultMap() {
        when(profileClient.getBulkUserProfiles(any())).thenReturn(
                ApiResponse.<Map<String, UserProfileResponse>>builder()
                        .result(Map.of("user-1", UserProfileResponse.builder().userId("user-1").build())).build());

        Map<String, UserProfileResponse> response = socketDownstreamService.getBulkUserProfiles(Set.of("user-1"));

        assertThat(response).containsKey("user-1");
    }

    @Test
    void getBulkUserProfiles_nullResult_returnsEmptyMap() {
        when(profileClient.getBulkUserProfiles(any())).thenReturn(ApiResponse.<Map<String, UserProfileResponse>>builder().result(null).build());

        assertThat(socketDownstreamService.getBulkUserProfiles(Set.of("user-1"))).isEmpty();
    }

    @Test
    void getBulkUserProfilesFallback_profileServiceDown_returnsEmptyMapInsteadOfThrowing() {
        Map<String, UserProfileResponse> response = socketDownstreamService.getBulkUserProfilesFallback(
                Set.of("user-1"), new RuntimeException("profile-service down"));

        assertThat(response).isEmpty();
    }
}
