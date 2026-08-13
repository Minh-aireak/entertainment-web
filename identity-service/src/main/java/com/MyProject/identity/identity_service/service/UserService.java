package com.MyProject.identity.identity_service.service;

import java.time.LocalDateTime;
import java.util.*;

import com.MyProject.identity.identity_service.dto.event.UserRegisteredEvent;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.identity.identity_service.dto.request.*;
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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    private static final String ADMIN_ROLE = "ADMIN";

    UserRepository userRepository;
    UserMapper userMapper;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    OutboxEventPublisher outboxEventPublisher;
    AuthenticationService authenticationService;

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

            UserRegisteredEvent userRegisteredEvent = UserRegisteredEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .userId(user.getId())
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .displayName(request.getUsername())
                    .joinDate(LocalDateTime.now())
                    .build();

            outboxEventPublisher.publish(user.getId(), "user.registered", userRegisteredEvent);

        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
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

        // Force every other session (and the one that just changed the password) to re-login.
        authenticationService.revokeAllUserTokens(user.getId());
    }

    @Transactional(readOnly = true)
    public UserResponse getMyInfo() {
        User user = userRepository.findById(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage = userRepository.findAllWithoutRole(ADMIN_ROLE, pageable);
        
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

        if (!user.isActive()) {
            authenticationService.revokeAllUserTokens(user.getId());
        }

        return user.isActive() ? "Account activated successfully!" : "Account deactivated successfully!";
    }
}
