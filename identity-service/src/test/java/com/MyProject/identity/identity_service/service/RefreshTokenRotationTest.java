package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.entity.RefreshToken;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.repository.RefreshTokenRepository;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RoleRepository roleRepository;
    @Mock ResetPasswordRepository resetPasswordRepository;
    @Mock OutboxEventPublisher outboxEventPublisher;
    @Mock RefreshTokenRepository refreshTokenRepository;

    @InjectMocks AuthenticationService authenticationService;

    @Test
    void refreshRotatesOnlyPresentedSession() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", Character.toString('a').repeat(64));
        ReflectionTestUtils.setField(authenticationService, "validDuration", 3600L);
        ReflectionTestUtils.setField(authenticationService, "refreshableDuration", 360000L);

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(UUID.randomUUID().toString())
                .active(true)
                .roles(Collections.emptySet())
                .build();
        String presentedToken = UUID.randomUUID().toString();
        RefreshToken storedToken = RefreshToken.builder()
                .token(presentedToken)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(presentedToken)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authenticationService.refreshTokens(presentedToken);

        assertTrue(storedToken.isRevoked());
        assertNotEquals(presentedToken, response.getRefreshToken());
        verify(refreshTokenRepository, never()).findByUserIdAndRevokedFalse(user.getId());
    }
}
