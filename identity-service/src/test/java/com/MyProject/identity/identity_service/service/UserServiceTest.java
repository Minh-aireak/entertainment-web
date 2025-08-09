//package com.MyProject.identity.identity_service.service;
//
//import com.MyProject.identity.identity_service.dto.request.UserCreationRequest;
//import com.MyProject.identity.identity_service.dto.request.UserUpdateRequest;
//import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
//import com.MyProject.identity.identity_service.dto.response.RoleResponse;
//import com.MyProject.identity.identity_service.dto.response.UserResponse;
//import com.MyProject.identity.identity_service.entity.Permission;
//import com.MyProject.identity.identity_service.entity.Role;
//import com.MyProject.identity.identity_service.entity.User;
//import com.MyProject.identity.identity_service.exception.AppException;
//import com.MyProject.identity.identity_service.mapper.UserMapper;
//import com.MyProject.identity.identity_service.repository.RoleRepository;
//import com.MyProject.identity.identity_service.repository.UserRepository;
//import jakarta.transaction.Transactional;
//import lombok.AccessLevel;
//import lombok.experimental.FieldDefaults;
//import lombok.extern.slf4j.Slf4j;
//import org.assertj.core.api.Assertions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//
//import java.time.LocalDate;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Optional;
//import java.util.Set;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//
//@SpringBootTest
//@Slf4j
//@FieldDefaults(level = AccessLevel.PRIVATE)
//@AutoConfigureMockMvc
//@ActiveProfiles("test")
//@TestPropertySource("/test.properties")
//class UserServiceTest {
//    @Autowired
//    UserService userService;
//
//    @MockitoBean
//    UserMapper userMapper;
//
//    @MockitoBean
//    UserRepository userRepository;
//
//    @MockitoBean
//    RoleRepository roleRepository;
//
//    @MockitoBean
//    PasswordEncoder passwordEncoder;
//
//    User user;
//    User afterUpdateUser;
//    User userNew;
//    UserResponse userResponse;
//    UserResponse userResponseNew;
//    LocalDate dob;
//    Permission permission;
//    PermissionResponse permissionResponse;
//    Role role1;
//    Role role2;
//    RoleResponse roleResponse;
//    RoleResponse roleResponseNew;
//    UserCreationRequest creationRequest;
//    UserUpdateRequest updateRequest;
//
//    @BeforeEach
//    void initData(){
//        dob = LocalDate.of(2005, 12, 18);
//
//        permission = Permission.builder()
//                .name("CREATE_TRIP")
//                .description("Create the trip")
//                .build();
//
//        permissionResponse = PermissionResponse.builder()
//                .name("CREATE_TRIP")
//                .description("Create the trip")
//                .build();
//
//        role1 = Role.builder()
//                .name("USER")
//                .description("User role")
//                .permissions(Set.of(permission))
//                .build();
//
//        role2 = Role.builder()
//                .name("USER2")
//                .description("User role")
//                .permissions(Set.of(permission))
//                .build();
//
//        roleResponse = RoleResponse.builder()
//                .name("USER")
//                .description("User role")
//                .permissions(Set.of(permissionResponse))
//                .build();
//
//        roleResponseNew = RoleResponse.builder()
//                .name("USER2")
//                .description("User role")
//                .permissions(Set.of(permissionResponse))
//                .build();
//
//        user = User.builder()
//                .id("123456789")
//                .username("NguyenTuanMinh")
//                .password("1801062012")
//                .firstName("Nguyen")
//                .lastName("Minh")
//                .dob(dob)
//                .roles(Set.of(role1))
//                .build();
//
//        userNew = User.builder()
//                .id("123456789")
//                .username("NguyenTuanMinh")
//                .password("1801062012")
//                .firstName("Nguyen")
//                .lastName("Aireak")
//                .dob(dob)
//                .roles(Set.of(role2))
//                .build();
//
//        userResponse = UserResponse.builder()
//                .id("123456789")
//                .username("NguyenTuanMinh")
//                .firstName("Nguyen")
//                .lastName("Minh")
//                .dob(dob)
//                .roles(Set.of(roleResponse))
//                .build();
//
//        userResponseNew = UserResponse.builder()
//                .id("123456789")
//                .username("NguyenTuanMinh")
//                .firstName("Nguyen")
//                .lastName("Aireak")
//                .dob(dob)
//                .roles(Set.of(roleResponseNew))
//                .build();
//
//        creationRequest = UserCreationRequest.builder()
//                .username("NguyenTuanMinh")
//                .password("1801062012")
//                .firstName("Nguyen")
//                .lastName("Minh")
//                .dob(dob)
//                .build();
//
//        updateRequest = UserUpdateRequest.builder()
//                .lastName("Aireak")
//                .password("Minh2005@")
//                .roles(new HashSet<>(List.of("USER2")))
//                .build();
//    }
//
//    @Test
//    void createsUser_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
//        Assertions.assertThat(UserService.class.getMethod("createUser", UserCreationRequest.class)
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    void createUser_success(){
//        when(roleRepository.findById(any())).thenReturn(Optional.of(role1));
//        when(userMapper.toUser(any())).thenReturn(user);
//        when(userRepository.save(any())).thenReturn(user);
//        when(userMapper.toUserResponse(user)).thenReturn(userResponse);
//
//        var actual = userService.createUser(creationRequest);
//
//        verify(roleRepository).findById(any());
//        verify(userMapper, times(1)).toUser(any());
//        verify(userRepository, times(1)).save(any());
//        verify(userMapper, times(1)).toUserResponse(any());
//        Assertions.assertThat(actual).isSameAs(userResponse);
//
//        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(userResponse);
//    }
//
//    @Test
//    void createUser_roleNotExisted_return1007(){
//        when(roleRepository.findById(any())).thenReturn(Optional.empty());
//
//        var exception = assertThrows(
//                AppException.class, () -> userService.createUser(creationRequest));
//
//        verify(roleRepository, times(1)).findById(any());
//
//        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1007);
//        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("Role not existed!");
//    }
//
//    @Test
//    void createUser_usernameExisted_return1001(){
//        when(roleRepository.findById(any())).thenReturn(Optional.of(role1));
//        when(userMapper.toUser(creationRequest)).thenReturn(user);
//        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("User existed!"));
//
//        var exception = assertThrows(
//                AppException.class, () -> userService.createUser(creationRequest));
//
//        verify(roleRepository, times(1)).findById(any());
//        verify(userMapper, times(1)).toUser(creationRequest);
//        verify(userRepository, times(1)).save(any());
//
//        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1001);
//        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("User existed!");
//    }
//
//    @Test
//    void changePassword_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
//        Assertions.assertThat(UserService.class.getMethod("changePassword", String.class, UserUpdateRequest.class)
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    void changePassword_success(){
//        when(userRepository.findById("123456789")).thenReturn(Optional.of(user));
//        doNothing().when(userMapper).update(user, updateRequest);
//        when(roleRepository.findAllById(updateRequest.getRoles())).thenReturn(List.of(role2));
//        when(userRepository.save(user)).thenReturn(userNew);
//        when(userMapper.toUserResponse(userNew)).thenReturn(userResponseNew);
//
//        var actual = userService.changePassword("123456789", updateRequest);
//
//        verify(userRepository).findById("123456789");
//        verify(userMapper, times(1)).update(user, updateRequest);
//        verify(roleRepository, times(1)).findAllById(updateRequest.getRoles());
//        verify(userRepository).save(user);
//        verify(userMapper).toUserResponse(userNew);
//        Assertions.assertThat(actual).isSameAs(userResponseNew);
//
//        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(userResponseNew);
//    }
//
//    @Test
//    void changePasswordNotExisted_return1001(){
//        when(userRepository.findById("123456789")).thenReturn(Optional.empty());
//
//        var exception = assertThrows(AppException.class,
//                () -> userService.changePassword("123456789", updateRequest));
//
//        verify(userRepository).findById("123456789");
//
//        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
//        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("User not existed!");
//    }
//
//    @Test
//    void changePassword_passwordNull_notCallPasswordEncoder(){
//        updateRequest.setPassword(null);
//
//        when(userRepository.findById(any())).thenReturn(Optional.of(user));
//        when(roleRepository.findAllById(updateRequest.getRoles())).thenReturn(List.of(role2));
//        when(userRepository.save(user)).thenReturn(userNew);
//        when(userMapper.toUserResponse(userNew)).thenReturn(userResponseNew);
//
//        var actual = userService.changePassword("123456789", updateRequest);
//
//        verify(userRepository).findById("123456789");
//        verify(userMapper, times(1)).update(user, updateRequest);
//        verify(passwordEncoder, never()).encode(updateRequest.getPassword());
//        verify(roleRepository, times(1)).findAllById(updateRequest.getRoles());
//        verify(userRepository).save(user);
//        verify(userMapper).toUserResponse(userNew);
//        Assertions.assertThat(actual).isSameAs(userResponseNew);
//
//        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(userResponseNew);
//    }
//
//    @Test
//    void changePassword_passwordNotNull_callPasswordEncoder(){
//        when(userRepository.findById(any())).thenReturn(Optional.of(user));
//        when(passwordEncoder.encode(updateRequest.getPassword())).thenReturn("encode!");
//        when(roleRepository.findAllById(updateRequest.getRoles())).thenReturn(List.of(role2));
//        when(userRepository.save(user)).thenReturn(userNew);
//        when(userMapper.toUserResponse(userNew)).thenReturn(userResponseNew);
//
//        var actual = userService.changePassword("123456789", updateRequest);
//
//        verify(userRepository).findById("123456789");
//        verify(userMapper, times(1)).update(user, updateRequest);
//        verify(passwordEncoder, times(1)).encode(updateRequest.getPassword());
//        verify(roleRepository, times(1)).findAllById(updateRequest.getRoles());
//        verify(userRepository).save(user);
//        verify(userMapper).toUserResponse(userNew);
//
//        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(userResponseNew);
//    }
//
//    @Test
//    void changePassword_rolesNull_notCallRoleRepository(){
//        updateRequest.setRoles(null);
//        userResponse.setRoles(Set.of(roleResponse));
//        when(userRepository.findById("123456789")).thenReturn(Optional.of(user));
//        doNothing().when(userMapper).update(user, updateRequest);
//        when(userRepository.save(user)).thenReturn(userNew);
//        when(userMapper.toUserResponse(userNew)).thenReturn(userResponseNew);
//
//        var actual = userService.changePassword("123456789", updateRequest);
//
//        verify(userRepository).findById("123456789");
//        verify(userMapper, times(1)).update(user, updateRequest);
//        verify(roleRepository, never()).findAllById(any());
//        verify(userRepository).save(user);
//        verify(userMapper).toUserResponse(userNew);
//        Assertions.assertThat(actual).isSameAs(userResponseNew);
//
//        Assertions.assertThat(actual).usingRecursiveComparison().isEqualTo(userResponseNew);
//    }
//
//    @Test
//    void deleteUser_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
//        Assertions.assertThat(UserService.class.getMethod("deleteUser", String.class)
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    void deleteUser_success(){
//        when(userRepository.existsById("123456789")).thenReturn(true);
//        doNothing().when(userRepository).deleteById("123456789");
//
//        userService.deleteUser("123456789");
//
//        verify(userRepository).existsById("123456789");
//        verify(userRepository, times(1)).deleteById("123456789");
//    }
//
//    @Test
//    void deleteUser_userNotExisted_return(){
//        when(userRepository.existsById("123456789")).thenReturn(false);
//
//        var exception = assertThrows(AppException.class, () -> userService.deleteUser("123456789"));
//
//        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
//        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("User not existed!");
//    }
//
//    @Test
//    void getMyInfo_shouldHaveTransactionalAnnotation() throws Exception{
//        Assertions.assertThat(UserService.class.getMethod("getMyInfo")
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    @WithMockUser(username = "NguyenTuanMinh")
//    void getMyInfo_success(){
//        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.of(user));
//        when(userMapper.toUserResponse(user)).thenReturn(userResponse);
//
//        var response = userService.getMyInfo();
//
//        verify(userRepository).findByUsername("NguyenTuanMinh");
//        verify(userMapper, times(1)).toUserResponse(user);
//        Assertions.assertThat(response).isSameAs(userResponse);
//
//        Assertions.assertThat(response).usingRecursiveComparison().isEqualTo(userResponse);
//    }
//
//    @Test
//    @WithMockUser(username = "NguyenTuanMinh")
//    void getMyInfo_userNotExisted_return1002(){
//        when(userRepository.findByUsername("NguyenTuanMinh")).thenReturn(Optional.empty());
//
//        var exception = assertThrows(AppException.class, () -> userService.getMyInfo());
//
//        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
//        Assertions.assertThat(exception.getErrorCode().getMessage()).isEqualTo("User not existed!");
//    }
//
//    @Test
//    void getAllUsers_shouldHaveTransactionalAnnotation() throws Exception{
//        Assertions.assertThat(UserService.class.getMethod("getAllUsers")
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    void getAllUsers_success(){
//        when(userRepository.findAll()).thenReturn(List.of(user));
//        when(userMapper.toListUserResponse(List.of(user))).thenReturn(List.of(userResponse));
//
//        var response = userService.getAllUsers();
//
//        verify(userRepository).findAll();
//        verify(userMapper).toListUserResponse(List.of(user));
//
//        Assertions.assertThat(response).usingRecursiveComparison()
//                .isEqualTo(List.of(userResponse));
//    }
//
//    @Test
//    void getUser_shouldHaveTransactionalAnnotation() throws Exception{
//        Assertions.assertThat(UserService.class.getMethod("getUser", String.class)
//                .isAnnotationPresent(Transactional.class));
//    }
//
//    @Test
//    void getUser_success(){
//        when(userRepository.findById("123456789")).thenReturn(Optional.of(user));
//        when(userMapper.toUserResponse(user)).thenReturn(userResponse);
//
//        var response = userService.getUser("123456789");
//
//        verify(userRepository).findById("123456789");
//        verify(userMapper).toUserResponse(user);
//        Assertions.assertThat(response).isSameAs(userResponse);
//
//        Assertions.assertThat(response).usingRecursiveComparison()
//                .isEqualTo(userResponse);
//    }
//
//    @Test
//    void getUser_userNotExists_return1002() {
//        when(userRepository.findById(any())).thenReturn(Optional.empty());
//
//        var exception = assertThrows(AppException.class,
//                () -> userService.getUser("123"));
//
//        verify(userRepository).findById("123");
//        verify(userMapper, never()).toUserResponse(any());
//
//        assertEquals(exception.getErrorCode().getCode(), 1002);
//        assertEquals(exception.getErrorCode().getMessage(), "User not existed!");
//    }
//}
