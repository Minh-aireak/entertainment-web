package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.UserMapper;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import com.MyProject.common.dto.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @InjectMocks
    UserService userService;

    @Mock
    UserMapper userMapper;

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    OutboxEventPublisher outboxEventPublisher;

    @Mock
    AuthenticationService authenticationService;

    User user;
    UserResponse userResponse;
    Role role1;
    UserCreationRequest creationRequest;
    ChangePasswordRequest changePasswordRequest;
    List<User> users;
    List<UserResponse> userResponses;

    @BeforeEach
    void initData(){
        role1 = Role.builder()
                .name("USER")
                .description("User role")
                .build();

        RoleResponse roleResponse = RoleResponse.builder()
                .name("USER")
                .description("User role")
                .build();

        user = User.builder()
                .id("123456789")
                .username("aireak")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .roles(Set.of(role1))
                .active(true)
                .build();

        userResponse = UserResponse.builder()
                .id("123456789")
                .username("aireak")
                .roles(Set.of(roleResponse))
                .active(true)
                .build();

        creationRequest = UserCreationRequest.builder()
                .username("aireak")
                .password("REDACTED_LEGACY_CREDENTIAL")
                .build();

        changePasswordRequest = ChangePasswordRequest.builder()
                .oldPassword("REDACTED_LEGACY_CREDENTIAL")
                .newPassword("1801062010")
                .build();

        users = List.of(user);
        userResponses = List.of(userResponse);
    }

    private void mockAuthenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("aireak");

        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUser_success(){
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role1));
        when(userMapper.toUser(creationRequest)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("encoded!");
        when(userRepository.save(any())).thenReturn(user);
        when(userMapper.toUserResponse(user)).thenReturn(userResponse);

        var response = userService.createUser(creationRequest);

        assertNotNull(response);
        assertThat(response).isSameAs(userResponse);

        verify(roleRepository, times(1)).findById("USER");
        verify(userMapper, times(1)).toUser(creationRequest);
        verify(passwordEncoder, times(1)).encode("REDACTED_LEGACY_CREDENTIAL");
        verify(userRepository, times(1)).save(user);
        verify(outboxEventPublisher, times(1)).publish(eq(user.getId()), eq("user.registered"), any());
        verify(userMapper, times(1)).toUserResponse(any());
    }

    @Test
    void createUser_roleNotExisted(){
        when(roleRepository.findById(any())).thenReturn(Optional.empty());

        var exception = assertThrows(
                AppException.class, () -> userService.createUser(creationRequest));

        assertEquals(ErrorCode.ROLE_NOT_EXISTED, exception.getErrorCode());

        verify(roleRepository, times(1)).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_usernameExisted(){
        when(roleRepository.findById("USER")).thenReturn(Optional.of(role1));
        when(userMapper.toUser(creationRequest)).thenReturn(user);
        when(passwordEncoder.encode(any())).thenReturn("encoded!");

        when(userRepository.save(any(User.class)))
                .thenThrow(DataIntegrityViolationException.class);

        AppException exception = assertThrows(AppException.class, () -> userService.createUser(creationRequest));

        assertEquals(ErrorCode.USERNAME_EXISTED, exception.getErrorCode());

        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void changePassword_success(){
        mockAuthenticatedUser();

        when(userRepository.findByUsername("aireak")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(changePasswordRequest.getNewPassword())).thenReturn("encoded!");

        userService.changePassword(changePasswordRequest);

        assertEquals("encoded!", user.getPassword());

        verify(userRepository, times(1)).findByUsername("aireak");
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, times(1)).encode(changePasswordRequest.getNewPassword());
        verify(userRepository).save(user);
        verify(authenticationService).revokeAllUserTokens(user.getId());
    }

    @Test
    void changePassword_unAuthorized(){
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(null);

        AppException exception = assertThrows(AppException.class,
                () -> userService.changePassword(changePasswordRequest));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(userRepository, never()).findByUsername(any());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotExisted(){
        mockAuthenticatedUser();
        when(userRepository.findByUsername("aireak")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userService.changePassword(changePasswordRequest));

        assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername("aireak");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_oldPasswordIncorect(){
        mockAuthenticatedUser();
        when(userRepository.findByUsername("aireak")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())).thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> userService.changePassword(changePasswordRequest));

        assertEquals(ErrorCode.PASSWORD_INCORRECT, exception.getErrorCode());

        verify(userRepository, times(1)).findByUsername("aireak");
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUsers_success() {
        Page<User> page = new PageImpl<>(users, PageRequest.of(0, 10), 1);
        when(userRepository.findAllWithoutRole("ADMIN", PageRequest.of(0, 10))).thenReturn(page);
        when(userMapper.toListUserResponse(users)).thenReturn(userResponses);

        PageResponse<UserResponse> response = userService.getUsers(0, 10);

        assertEquals(1, response.getData().size());
        assertEquals(1, response.getTotalElement());
        assertThat(response.getData()).isSameAs(userResponses);

        verify(userRepository, times(1)).findAllWithoutRole("ADMIN", PageRequest.of(0, 10));
        verify(userMapper, times(1)).toListUserResponse(users);
    }

    @Test
    void toggleAccount_activeUser_deactivatesAndReturnsMessage() {
        when(userRepository.findById("123456789")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        String message = userService.toggleAccount("123456789");

        assertFalse(user.isActive());
        assertEquals("Account deactivated successfully!", message);
        verify(userRepository, times(1)).findById("123456789");
        verify(userRepository, times(1)).save(user);
        verify(authenticationService).revokeAllUserTokens(user.getId());
    }

    @Test
    void toggleAccount_inactiveUser_activatesAndReturnsMessage() {
        user.setActive(false);
        when(userRepository.findById("123456789")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        String message = userService.toggleAccount("123456789");

        assertTrue(user.isActive());
        assertEquals("Account activated successfully!", message);
        verify(authenticationService, never()).revokeAllUserTokens(any());
    }

    @Test
    void toggleAccount_userNotExisted() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userService.toggleAccount("missing"));

        assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        verify(userRepository, never()).save(any());
    }
}
