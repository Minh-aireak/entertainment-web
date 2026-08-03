package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.EmailRequest;
import com.MyProject.identity.identity_service.dto.request.ForgotPasswordRequest;
import com.MyProject.identity.identity_service.dto.request.Recipient;
import com.MyProject.identity.identity_service.dto.request.ResetPasswordRequest;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PasswordResetService {
    UserRepository userRepository;
    ResetPasswordRepository resetPasswordRepository;
    PasswordEncoder passwordEncoder;
    OutboxEventPublisher outboxEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_EXISTED));

        ResetPassword resetPassword = ResetPassword.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(3600))
                .build();

        String resetUrl = "http://localhost:5173/reset-password?token=" + resetPassword.getToken();

        EmailRequest emailRequest = EmailRequest.builder()
                .to(List.of(Recipient.builder()
                        .email(request.getEmail())
                        .build()))
                .subject("Reset Your Password")
                .htmlContent(
                    "Hi " + user.getUsername() + ",\n\n" +
                    "We received a request to reset your password. " +
                    "Click the link below to reset your password:\n\n" +
                    resetUrl + "\n\n" +
                    "If you didn’t request this, you can ignore this email.\n\n" +
                    "Thanks!"
                )
                .build();

        outboxEventPublisher.publish(user.getId(), "email.sent", emailRequest);
        resetPasswordRepository.save(resetPassword);

        return "Check your email: " + request.getEmail();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        ResetPassword resetToken = resetPasswordRepository.findByToken(request.getToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN_RESET));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            resetPasswordRepository.delete(resetToken);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = Optional.ofNullable(resetToken.getUser())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        resetPasswordRepository.delete(resetToken);
    }
}
