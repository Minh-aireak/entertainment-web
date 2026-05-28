package com.MyProject.identity.identity_service.service;

import java.time.LocalDateTime;
import java.util.*;

import com.MyProject.identity.identity_service.dto.request.EmailRequest;
import com.MyProject.identity.identity_service.dto.request.Recipient;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.identity.identity_service.dto.event.UserCreatedEvent;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.UserMapper;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;

import com.MyProject.identity.identity_service.repository.OutboxRepository;
import com.MyProject.identity.identity_service.entity.Outbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    ResetPasswordRepository resetPasswordRepository;
    UserMapper userMapper;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public UserResponse createUser(UserCreationRequest request) {
        Role role = roleRepository.findById("USER").orElseThrow(()
                -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        User user = userMapper.toUser(request);

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        try {
            user = userRepository.save(user);

            // Create events for Outbox (CDC MySQL)
            // 1. User Created Event
            UserCreatedEvent userCreatedEvent = UserCreatedEvent.builder()
                    .userId(user.getId())
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .displayName(request.getUsername())
                    .joinDate(LocalDateTime.now())
                    .build();

            outboxRepository.save(Outbox.builder()
                    .topic("user.created")
                    .payload(objectMapper.writeValueAsString(userCreatedEvent))
                    .processed(false)
                    .build());

            // 2. Email Sent Event
            EmailRequest emailRequest = EmailRequest.builder()
                    .to(List.of(Recipient.builder()
                            .email(request.getEmail())
                            .build()))
                    .subject("Welcome to travelplanner!")
                    .htmlContent("Hello, " + request.getUsername())
                    .build();

            outboxRepository.save(Outbox.builder()
                    .topic("email.sent")
                    .payload(objectMapper.writeValueAsString(emailRequest))
                    .processed(false)
                    .build());

        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        } catch (JsonProcessingException e) {
            log.error("Error serializing outbox event", e);
            throw new RuntimeException("Failed to save outbox event", e);
        }

        return userMapper.toUserResponse(user);
    }

    private String getUserUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByUsername(getUserUsername()).orElseThrow(()
                -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword()))
            throw new AppException(ErrorCode.PASSWORD_INCORRECT);
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public PageResponse<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage = userRepository.findAll(pageable);
        
        return PageResponse.<UserResponse>builder()
                .currentPage(userPage.getNumber())
                .totalPages(userPage.getTotalPages())
                .pageSize(userPage.getSize())
                .totalElement((int) userPage.getTotalElements())
                .data(userMapper.toListUserResponse(userPage.getContent()))
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public String toggleAccount(String id) {
        User user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setActive(!user.isActive());
        userRepository.save(user);
        return user.isActive() ? "Account activated successfully!" : "Account deactivated successfully!";
    }

    @Transactional(rollbackFor = Exception.class)
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_EXISTED));

        ResetPassword resetPassword = ResetPassword.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(3600))
                .build();

        String resetUrl = "http://localhost:5173/password-reset-token?token=" + resetPassword.getToken();

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

        try {
            outboxRepository.save(Outbox.builder()
                    .topic("email.sent")
                    .payload(objectMapper.writeValueAsString(emailRequest))
                    .processed(false)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Error serializing outbox event", e);
            throw new RuntimeException("Failed to save outbox event", e);
        }

        resetPasswordRepository.save(resetPassword);

        return "Check your email: " + request.getEmail();
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        ResetPassword resetToken = resetPasswordRepository.findByToken(request.getToken())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN_RESET));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        resetPasswordRepository.delete(resetToken);
    }
}
