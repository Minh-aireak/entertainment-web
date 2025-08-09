//package com.MyProject.identity.identity_service.controller;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//import java.time.LocalDate;
//import java.util.List;
//import java.util.Set;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//import org.springframework.test.web.servlet.MockMvc;
//import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
//import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import com.MyProject.identity.identity_service.dto.request.UserCreationRequest;
//import com.MyProject.identity.identity_service.dto.request.UserUpdateRequest;
//import com.MyProject.identity.identity_service.dto.response.PermissionResponse;
//import com.MyProject.identity.identity_service.dto.response.RoleResponse;
//import com.MyProject.identity.identity_service.dto.response.UserResponse;
//import com.MyProject.identity.identity_service.exception.AppException;
//import com.MyProject.identity.identity_service.exception.ErrorCode;
//import com.MyProject.identity.identity_service.service.UserService;
//
//import lombok.AccessLevel;
//import lombok.experimental.FieldDefaults;
//
//@SpringBootTest
//@ExtendWith(SpringExtension.class)
//@FieldDefaults(level = AccessLevel.PRIVATE)
//@AutoConfigureMockMvc
//@TestPropertySource("/test.properties")
//class UserControllerTest {
//    @Autowired
//    MockMvc mockMvc;
//
//    @MockitoBean
//    UserService userService;
//
//    UserCreationRequest creationRequest;
//    UserUpdateRequest updateRequest;
//    UserResponse userResponse;
//    RoleResponse roleResponse;
//    LocalDate dob;
//    ObjectMapper objectMapper;
//
//    @BeforeEach
//    void initData() {
//        objectMapper = new ObjectMapper();
//        objectMapper.registerModule(new JavaTimeModule());
//
//        dob = LocalDate.of(2005, 12, 18);
//
//        creationRequest = UserCreationRequest.builder()
//                .username("NguyenTuanMinh")
//                .password("REDACTED_LEGACY_CREDENTIAL")
//                .firstName("Nguyen")
//                .lastName("Minh")
//                .dob(dob)
//                .build();
//
//        updateRequest = UserUpdateRequest.builder()
//                .password("18122005")
//                .firstName("Aireak")
//                .lastName("Aireak")
//                .dob(LocalDate.of(2005, 7, 12))
//                .build();
//
//        PermissionResponse createUser = new PermissionResponse("CREATE_USER", "Create new user");
//        PermissionResponse deleteUser = new PermissionResponse("APPROVE_DATA", "Approve data");
//
//        roleResponse = RoleResponse.builder()
//                .name("ADMIN")
//                .description("Admin role")
//                .permissions(Set.of(createUser, deleteUser))
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
//    }
//
//    @Test
//    void createUser_validRequest_success() throws Exception {
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        when(userService.createUser(any())).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.id").value("123456789"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("NguyenTuanMinh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Nguyen"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("Minh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.dob").value(String.valueOf(dob)));
//    }
//
//    @Test
//    void createUser_invalidUsername_isNull() throws Exception {
//        creationRequest.setUsername(null);
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1017))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Username cannot be null!"));
//    }
//
//    @Test
//    void createUser_invalidUsername_isSize() throws Exception {
//        creationRequest.setUsername("1232");
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1003));
//    }
//
//    @Test
//    void createUser_invalidPassword_isNull() throws Exception {
//        creationRequest.setPassword(null);
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1018))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Password cannot be null!"));
//    }
//
//    @Test
//    void createUser_invalidPassword_isSize() throws Exception {
//        creationRequest.setPassword("1111111");
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1004));
//    }
//
//    @Test
//    void createUser_invalidDob_isNull() throws Exception {
//        creationRequest.setDob(null);
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1019))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Day of birth cannot be null!"));
//    }
//
//    @Test
//    void createUser_invalidDob_isConstraint() throws Exception {
//        creationRequest.setDob(LocalDate.of(2025, 12, 18));
//        String content = objectMapper.writeValueAsString(creationRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/create")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1020));
//    }
//
//    @Test
//    @WithMockUser(username = "NguyenTuanMinh")
//    void updateUser_invalidRequest_success() throws Exception {
//        userResponse.setFirstName("Aireak");
//        userResponse.setLastName("Aireak");
//        userResponse.setDob(LocalDate.of(2005, 7, 12));
//        String content = objectMapper.writeValueAsString(updateRequest);
//
//        when(userService.updateUser(updateRequest)).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/123456789")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.id").value("123456789"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("NguyenTuanMinh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Aireak"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("Aireak"))
//                .andExpect(
//                        MockMvcResultMatchers.jsonPath("result.dob").value(String.valueOf(LocalDate.of(2005, 7, 12))));
//    }
//
//    @Test
//    @WithMockUser(username = "OtherUser")
//    void updateUser_invalidRequest_unAuthority() throws Exception {
//        userResponse.setFirstName("Aireak");
//        userResponse.setLastName("Aireak");
//        userResponse.setDob(LocalDate.of(2005, 7, 12));
//        String content = objectMapper.writeValueAsString(updateRequest);
//
//        when(userService.updateUser(updateRequest)).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/123456789")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isForbidden())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));
//    }
//
//    @Test
//    void updateUser_invalidRequest_unAuthentication() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/123456789").contentType(MediaType.APPLICATION_JSON))
//                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));
//    }
//
//    @Test
//    @WithMockUser(username = "AnyUserStillPassed")
//    void updateUser_invalidPassword_isSize() throws Exception {
//        updateRequest.setPassword("1111111");
//        String content = objectMapper.writeValueAsString(updateRequest);
//
//        when(userService.updateUser(updateRequest)).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/123456789")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1004));
//    }
//
//    @Test
//    @WithMockUser(username = "AnyUserStillPassed")
//    void updateUser_invalidDob_isConstraint() throws Exception {
//        updateRequest.setDob(LocalDate.of(2025, 12, 18));
//        String content = objectMapper.writeValueAsString(updateRequest);
//
//        mockMvc.perform(MockMvcRequestBuilders.post("/users/123456789")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(content))
//                .andExpect(MockMvcResultMatchers.status().isBadRequest())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1020));
//    }
//
//    @Test
//    @WithMockUser(authorities = "DELETE_USER")
//    void deleteUser_invalidRequest_success() throws Exception {
//        doNothing().when(userService).deleteUser("123456789");
//
//        mockMvc.perform(MockMvcRequestBuilders.delete("/users/123456789"))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User has been deleted!"));
//
//        verify(userService, times(1)).deleteUser("123456789");
//    }
//
//    @Test
//    @WithMockUser(authorities = "OtherPermissions")
//    void deleteUser_unAuthority_return403() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.delete("/users/123456789"))
//                .andExpect(MockMvcResultMatchers.status().isForbidden())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));
//    }
//
//    @Test
//    void deleteUser_unAuthentication_return401() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.delete("/users/123456789"))
//                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));
//    }
//
//    @Test
//    @WithMockUser(authorities = "DELETE_USER")
//    void deleteUser_userNotExisted_returnAppException() throws Exception {
//        doThrow(new AppException(ErrorCode.USER_NOT_EXISTED)).when(userService).deleteUser(any());
//
//        mockMvc.perform(MockMvcRequestBuilders.delete("/users/userNotExisted"))
//                .andExpect(MockMvcResultMatchers.status().isNotFound())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1002))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("User not existed!"));
//    }
//
//    @Test
//    @WithMockUser(username = "UserAlreadyLogin")
//    void getMyInfo_invalidRequest_success() throws Exception {
//        when(userService.getMyInfo()).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/myInfo"))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.id").value("123456789"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("NguyenTuanMinh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Nguyen"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("Minh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.dob").value(String.valueOf(dob)));
//
//        verify(userService, times(1)).getMyInfo();
//    }
//
//    @Test
//    void getMyInfo_unAuthentication_return401() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/myInfo"))
//                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));
//    }
//
//    @Test
//    @WithMockUser(authorities = "GET_USERS")
//    void getAllUsers_validAuthority_success() throws Exception {
//        when(userService.getAllUsers()).thenReturn(List.of(userResponse));
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/read"))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("result[0].id").value("123456789"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result[0].username").value("NguyenTuanMinh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result[0].firstName").value("Nguyen"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result[0].lastName").value("Minh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result[0].dob").value(String.valueOf(dob)));
//
//        verify(userService, times(1)).getAllUsers();
//    }
//
//    @Test
//    @WithMockUser(authorities = "OtherPermissions")
//    void getAllUsers_invalidAuthority_return403() throws Exception {
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/read"))
//                .andExpect(MockMvcResultMatchers.status().isForbidden())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));
//
//        verify(userService, never()).getAllUsers();
//    }
//
//    @Test
//    void getAllUsers_unAuthentication_return401() throws Exception {
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/read"))
//                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));
//
//        verify(userService, never()).getAllUsers();
//    }
//
//    @Test
//    @WithMockUser(authorities = "GET_USER")
//    void getAllUser_validAuthority_success() throws Exception {
//        when(userService.getUser("123456789")).thenReturn(userResponse);
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/123456789"))
//                .andExpect(MockMvcResultMatchers.status().isOk())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.id").value("123456789"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("NguyenTuanMinh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Nguyen"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("Minh"))
//                .andExpect(MockMvcResultMatchers.jsonPath("result.dob").value(String.valueOf(dob)));
//
//        verify(userService, times(1)).getUser("123456789");
//    }
//
//    @Test
//    @WithMockUser(authorities = "OtherPermissions")
//    void getUser_invalidAuthority_return403() throws Exception {
//
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/123456789"))
//                .andExpect(MockMvcResultMatchers.status().isForbidden())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1013))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("You don't have permission!"));
//
//        verify(userService, never()).getAllUsers();
//    }
//
//    @Test
//    void getUser_unAuthentication_return401() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.get("/users/read"))
//                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
//                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1012))
//                .andExpect(MockMvcResultMatchers.jsonPath("message").value("Unauthenticated!"));
//
//        verify(userService, never()).getUser("123456789");
//    }
//}
