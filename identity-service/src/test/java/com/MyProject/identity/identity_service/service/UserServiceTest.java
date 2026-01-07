package com.MyProject.identity.identity_service.service;

import com.MyProject.common.dto.request.EmailRequest;
import com.MyProject.identity.identity_service.dto.request.*;
import com.MyProject.identity.identity_service.dto.response.RoleResponse;
import com.MyProject.identity.identity_service.dto.response.UserResponse;
import com.MyProject.identity.identity_service.entity.ResetPassword;
import com.MyProject.identity.identity_service.entity.Role;
import com.MyProject.identity.identity_service.entity.User;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.mapper.UserMapper;
import com.MyProject.identity.identity_service.repository.ResetPasswordRepository;
import com.MyProject.identity.identity_service.repository.RoleRepository;
import com.MyProject.identity.identity_service.repository.UserRepository;
import com.MyProject.identity.identity_service.repository.httpclient.UserProfileClient;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceTest {
    @Autowired
    UserService userService;

    @MockitoBean
    UserMapper userMapper;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    ResetPasswordRepository resetPasswordRepository;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    @MockitoBean
    UserProfileClient client;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    User user;
    UserResponse userResponse;
    Role role1;
    UserCreationRequest creationRequest;
    ChangePasswordRequest changePasswordRequest;
    ForgotPasswordRequest forgotPasswordRequest;
    ResetPasswordRequest resetPasswordRequest;
    ResetPassword resetPassword;
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
                .password("1801062010")
                .build();

        users = List.of(user);
        userResponses = List.of(userResponse);

        forgotPasswordRequest = ForgotPasswordRequest.builder()
                .email("aireak@gmail.com")
                .build();

        resetPasswordRequest = ResetPasswordRequest.builder()
                .token("token")
                .password("1801062010")
                .build();

        resetPassword = ResetPassword.builder()
                .token("token")
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(3600))
                .build();
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
        assertThat(response).usingRecursiveComparison().isEqualTo(userResponse);

        verify(roleRepository, times(1)).findById("USER");
        verify(userMapper, times(1)).toUser(creationRequest);
        verify(passwordEncoder, times(1)).encode("REDACTED_LEGACY_CREDENTIAL");
        verify(userRepository, times(1)).save(user);
        verify(client).createProfile(any(UserProfileCreationRequest.class));
        verify(kafkaTemplate).send(eq("onboard-email"), any(EmailRequest.class));
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

        verify(client, never()).createProfile(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void changePassword_success(){
        mockAuthenticatedUser();

        when(userRepository.findByUsername("aireak")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(changePasswordRequest.getPassword())).thenReturn("encoded!");

        userService.changePassword(changePasswordRequest);

        assertEquals("encoded!", user.getPassword());

        verify(userRepository, times(1)).findByUsername("aireak");
        verify(passwordEncoder, times(1)).encode(changePasswordRequest.getPassword());
        verify(userRepository).save(user);
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
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getAllUsers_success() {
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.toListUserResponse(users)).thenReturn(userResponses);

        List<UserResponse> response = userService.getAllUsers();

        assertEquals(1, response.size());
        assertThat(response).isSameAs(userResponses);
        assertThat(response).usingRecursiveComparison().isEqualTo(userResponses);

        verify(userRepository, times(1)).findAll();
        verify(userMapper, times(1)).toListUserResponse(users);
    }

    @Test
    void disableUser_success() {
        when(userRepository.findByUsername("aireak")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.disableUser("aireak");

        assertFalse(user.isActive());

        verify(userRepository, times(1)).findByUsername("aireak");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void disableUser_userNotExisted() {
        when(userRepository.findById("aireak")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userService.disableUser("aireak"));

        assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());

        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPassword_success() {
        when(userRepository.findByEmail(forgotPasswordRequest.getEmail())).thenReturn(Optional.of(user));
        when(resetPasswordRepository.save(any())).thenReturn(resetPassword);

        String message = userService.forgotPassword(forgotPasswordRequest);

        assertEquals("Check your email: aireak@gmail.com", message);

        verify(userRepository, times(1)).findByEmail(forgotPasswordRequest.getEmail());
        verify(resetPasswordRepository, times(1)).save(any());
        verify(kafkaTemplate, times(1)).send(eq("send-email"), any(EmailRequest.class));
    }

    @Test
    void forgotPassword_emailNotExisted() {
        when(userRepository.findByEmail(forgotPasswordRequest.getEmail())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userService.forgotPassword(forgotPasswordRequest));

        assertEquals(ErrorCode.EMAIL_NOT_EXISTED, exception.getErrorCode());

        verify(userRepository, times(1)).findByEmail(any());
        verify(resetPasswordRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void resetPassword_success() {
        when(resetPasswordRepository.findByToken(resetPasswordRequest.getToken())).thenReturn(Optional.of(resetPassword));
        when(passwordEncoder.encode(resetPasswordRequest.getPassword())).thenReturn("encode!");

        userService.resetPassword(resetPasswordRequest);

        verify(resetPasswordRepository, times(1)).findByToken(resetPassword.getToken());
        verify(passwordEncoder, times(1)).encode(resetPasswordRequest.getPassword());
        verify(userRepository, times(1)).save(resetPassword.getUser());
        verify(resetPasswordRepository, times(1)).delete(resetPassword);

    }

    @Test
    void resetPassword_invalidTokenReset() {
        when(resetPasswordRepository.findByToken(resetPasswordRequest.getToken())).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> userService.resetPassword(resetPasswordRequest));

        assertEquals(ErrorCode.INVALID_TOKEN_RESET, exception.getErrorCode());

        verify(resetPasswordRepository, times(1)).findByToken(resetPasswordRequest.getToken());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(resetPasswordRepository, never()).delete(any());
    }

    @Test
    void resetPassword_tokenExpired() {
        when(resetPasswordRepository.findByToken(resetPasswordRequest.getToken())).thenReturn(Optional.of(resetPassword));
        resetPassword.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        AppException exception = assertThrows(AppException.class,
                () -> userService.resetPassword(resetPasswordRequest));

        assertEquals(ErrorCode.TOKEN_EXPIRED, exception.getErrorCode());

        verify(resetPasswordRepository, times(1)).findByToken(resetPasswordRequest.getToken());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(resetPasswordRepository, never()).delete(any());
    }
}
