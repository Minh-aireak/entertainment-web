package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.ForgotPasswordRequest;
import com.MyProject.identity.identity_service.dto.request.ResetPasswordRequest;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock ResetPasswordRepository resetPasswordRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock OutboxEventPublisher outboxEventPublisher;

    PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, resetPasswordRepository,
                passwordEncoder, outboxEventPublisher);
    }

    // ---------- forgotPassword ----------

    @Test
    void forgotPassword_emailNotFound_throwsEmailNotExisted() {
        when(userRepository.findByEmail("missing@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.forgotPassword(
                ForgotPasswordRequest.builder().email("missing@gmail.com").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_EXISTED);
        verifyNoInteractions(resetPasswordRepository, outboxEventPublisher);
    }

    @Test
    void forgotPassword_happyPath_savesTokenAndPublishesEmailEvent() {
        User user = User.builder().id("user-1").username("aireak").email("aireak@gmail.com").build();
        when(userRepository.findByEmail("aireak@gmail.com")).thenReturn(Optional.of(user));

        String message = passwordResetService.forgotPassword(
                ForgotPasswordRequest.builder().email("aireak@gmail.com").build());

        assertThat(message).isEqualTo("Check your email: aireak@gmail.com");
        verify(resetPasswordRepository).save(argThat(rp -> rp.getUser() == user && rp.getToken() != null));
        verify(outboxEventPublisher).publish(eq("user-1"), eq("email.sent"), any());
    }

    @Test
    void forgotPassword_eachCallGeneratesUniqueToken() {
        User user = User.builder().id("user-1").email("aireak@gmail.com").build();
        when(userRepository.findByEmail("aireak@gmail.com")).thenReturn(Optional.of(user));
        ArgumentCaptor<ResetPassword> captor = ArgumentCaptor.forClass(ResetPassword.class);

        passwordResetService.forgotPassword(ForgotPasswordRequest.builder().email("aireak@gmail.com").build());
        passwordResetService.forgotPassword(ForgotPasswordRequest.builder().email("aireak@gmail.com").build());

        verify(resetPasswordRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getToken()).isNotEqualTo(captor.getAllValues().get(1).getToken());
    }

    // ---------- resetPassword ----------

    @Test
    void resetPassword_tokenNotFound_throwsInvalidTokenReset() {
        when(resetPasswordRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder().token("missing").password("newpass1").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN_RESET);
    }

    @Test
    void resetPassword_expiredToken_deletesTokenAndThrowsTokenExpired() {
        User user = User.builder().id("user-1").build();
        ResetPassword expired = ResetPassword.builder().token("tok-1").user(user)
                .expiryDate(LocalDateTime.now().minusMinutes(1)).build();
        when(resetPasswordRepository.findByToken("tok-1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder().token("tok-1").password("newpass1").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);

        verify(resetPasswordRepository).delete(expired);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_tokenWithoutUser_throwsUserNotExisted() {
        ResetPassword orphanToken = ResetPassword.builder().token("tok-1").user(null)
                .expiryDate(LocalDateTime.now().plusMinutes(10)).build();
        when(resetPasswordRepository.findByToken("tok-1")).thenReturn(Optional.of(orphanToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(
                ResetPasswordRequest.builder().token("tok-1").password("newpass1").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_EXISTED);
    }

    @Test
    void resetPassword_happyPath_updatesPasswordAndDeletesToken() {
        User user = User.builder().id("user-1").password("old-encoded").build();
        ResetPassword valid = ResetPassword.builder().token("tok-1").user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10)).build();
        when(resetPasswordRepository.findByToken("tok-1")).thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("newpass1")).thenReturn("new-encoded");

        passwordResetService.resetPassword(ResetPasswordRequest.builder().token("tok-1").password("newpass1").build());

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        verify(userRepository).save(user);
        verify(resetPasswordRepository).delete(valid);
    }
}
