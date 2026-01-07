package com.MyProject.identity.identity_service.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.MyProject.common.dto.request.EmailRequest;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.httpclient.UserProfileClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
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
    UserProfileClient client;
    KafkaTemplate<String, Object> kafkaTemplate;

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
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }

        UserProfileCreationRequest userprofileRequest = UserProfileCreationRequest.builder()
                .userId(user.getId())
                .username(request.getUsername())
                .email(request.getEmail())
                .displayName(request.getUsername())
                .joinDate(LocalDateTime.now())
                .build();

        client.createProfile(userprofileRequest);

        EmailRequest emailRequest = EmailRequest.builder()
                .channel("EMAIL")
                .recipient(request.getEmail())
                .subject("Welcome to travelplanner!")
                .body("Hello, " + request.getUsername())
                .build();

        kafkaTemplate.send("onboard-email", emailRequest);

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

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<UserResponse> getAllUsers() {
        return userMapper.toListUserResponse(userRepository.findAll());
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableUser(String userId) {
        User user = userRepository.findByUsername(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setActive(false);
        userRepository.save(user);
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
                .channel("EMAIL")
                .recipient(request.getEmail())
                .subject("Reset Your Password")
                .body(
                    "Hi " + user.getUsername() + ",\n\n" +
                    "We received a request to reset your password. " +
                    "Click the link below to reset your password:\n\n" +
                    resetUrl + "\n\n" +
                    "If you didn’t request this, you can ignore this email.\n\n" +
                    "Thanks!"
                )
                .build();

        kafkaTemplate.send("send-email", emailRequest);
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
