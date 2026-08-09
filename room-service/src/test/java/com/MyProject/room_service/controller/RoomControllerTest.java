package com.MyProject.room_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.room_service.configuration.SecurityConfig;
import com.MyProject.room_service.dto.request.CreateRoomRequest;
import com.MyProject.room_service.dto.request.JoinRoomRequest;
import com.MyProject.room_service.dto.request.PlaybackUpdateRequest;
import com.MyProject.room_service.dto.request.RoomMessageCreateRequest;
import com.MyProject.room_service.dto.response.RoomListItemResponse;
import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.dto.response.RoomParticipantResponse;
import com.MyProject.room_service.dto.response.RoomResponse;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.enums.ParticipantRole;
import com.MyProject.room_service.enums.PlaybackAction;
import com.MyProject.room_service.enums.RoomStatus;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.service.RoomApiRateLimitService;
import com.MyProject.room_service.service.RoomMessageService;
import com.MyProject.room_service.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        RoomControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean RoomService roomService;
    @MockitoBean RoomMessageService roomMessageService;
    @MockitoBean RoomApiRateLimitService roomApiRateLimitService;

    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Test
    void createRoom_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder().filmId("film-1").build())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomService);
    }

    @Test
    void createRoom_authenticated_checksRateLimitThenDelegates() throws Exception {
        when(roomService.createRoom(any())).thenReturn(RoomResponse.builder().id("room-1").host(true).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder().filmId("film-1").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("room-1"));

        verify(roomApiRateLimitService).checkRoomCreate("user-1");
    }

    @Test
    void createRoom_invalidFilm_returns400WithInvalidFilmCode() throws Exception {
        when(roomService.createRoom(any())).thenThrow(new AppException(ErrorCode.INVALID_FILM));

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateRoomRequest.builder().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.INVALID_FILM.getCode()));
    }

    @Test
    void listPublicRooms_authenticated_returnsPage() throws Exception {
        when(roomService.listPublicRooms(1, 12)).thenReturn(
                PageResponse.<RoomListItemResponse>builder().currentPage(1).pageSize(12).totalPages(1).totalElement(1L)
                        .data(java.util.List.of(RoomListItemResponse.builder().id("room-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/public").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("room-1"));
    }

    @Test
    void listPublicRooms_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/public"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomService);
    }

    @Test
    void listMyRooms_authenticated_returnsPage() throws Exception {
        when(roomService.listMyRooms(1, 12)).thenReturn(
                PageResponse.<RoomListItemResponse>builder().currentPage(1).pageSize(12).totalPages(0)
                        .totalElement(0L).data(java.util.List.of()).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/my").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data").isArray())
                .andExpect(jsonPath("result.data").isEmpty());
    }

    @Test
    void getRoom_notFound_returns404WithRoomNotFoundCode() throws Exception {
        when(roomService.getRoom("missing")).thenThrow(new AppException(ErrorCode.ROOM_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/missing").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.ROOM_NOT_FOUND.getCode()));
    }

    @Test
    void getRoom_accessDenied_returns403WithRoomAccessDeniedCode() throws Exception {
        when(roomService.getRoom("room-1")).thenThrow(new AppException(ErrorCode.ROOM_ACCESS_DENIED));

        mockMvc.perform(MockMvcRequestBuilders.get("/room-1").with(asUser("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(ErrorCode.ROOM_ACCESS_DENIED.getCode()));
    }

    @Test
    void joinRoom_full_returns400WithRoomFullCode() throws Exception {
        when(roomService.joinRoom(eq("room-1"), any())).thenThrow(new AppException(ErrorCode.ROOM_FULL));

        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/join")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(JoinRoomRequest.builder().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.ROOM_FULL.getCode()));
    }

    @Test
    void joinRoom_noRequestBody_stillSucceeds() throws Exception {
        when(roomService.joinRoom(eq("room-1"), eq(null))).thenReturn(RoomResponse.builder().id("room-1").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/join").with(asUser("user-1")))
                .andExpect(status().isOk());
    }

    @Test
    void leaveRoom_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/leave").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(roomService).leaveRoom("room-1");
    }

    @Test
    void closeRoom_notHost_returns403WithUnauthorizedCode() throws Exception {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED)).when(roomService).closeRoom("room-1");

        mockMvc.perform(MockMvcRequestBuilders.delete("/room-1").with(asUser("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void closeRoom_host_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/room-1").with(asUser("host-1")))
                .andExpect(status().isOk());

        verify(roomService).closeRoom("room-1");
    }

    @Test
    void updatePlayback_authenticated_checksRateLimitThenDelegates() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/room-1/playback")
                        .with(asUser("host-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaybackUpdateRequest.builder().action(PlaybackAction.PAUSE).build())))
                .andExpect(status().isOk());

        verify(roomApiRateLimitService).checkRoomPlayback("host-1", "room-1");
        verify(roomService).updatePlayback(eq("room-1"), any());
    }

    @Test
    void updatePlayback_invalidAction_returns400WithInvalidPlaybackActionCode() throws Exception {
        doThrow(new AppException(ErrorCode.INVALID_PLAYBACK_ACTION)).when(roomService).updatePlayback(eq("room-1"), any());

        mockMvc.perform(MockMvcRequestBuilders.patch("/room-1/playback")
                        .with(asUser("host-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.INVALID_PLAYBACK_ACTION.getCode()));
    }

    @Test
    void listParticipants_authenticated_returnsList() throws Exception {
        when(roomService.listParticipants("room-1")).thenReturn(
                java.util.List.of(RoomParticipantResponse.builder().userId("user-1").role(ParticipantRole.HOST).build()));

        mockMvc.perform(MockMvcRequestBuilders.get("/room-1/participants").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result[0].userId").value("user-1"));
    }

    @Test
    void listMessages_authenticated_returnsPage() throws Exception {
        when(roomMessageService.listMessages("room-1", 1, 30)).thenReturn(
                PageResponse.<RoomMessageResponse>builder().currentPage(1).pageSize(30).totalPages(1).totalElement(1L)
                        .data(java.util.List.of(RoomMessageResponse.builder().id("m-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/room-1/messages").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("m-1"));
    }

    @Test
    void listMessages_notAParticipant_returns403WithNotAParticipantCode() throws Exception {
        when(roomMessageService.listMessages(eq("room-1"), anyInt(), anyInt()))
                .thenThrow(new AppException(ErrorCode.NOT_A_PARTICIPANT));

        mockMvc.perform(MockMvcRequestBuilders.get("/room-1/messages").with(asUser("user-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(ErrorCode.NOT_A_PARTICIPANT.getCode()));
    }

    @Test
    void sendMessage_authenticated_checksRateLimitThenDelegates() throws Exception {
        when(roomMessageService.sendMessage(eq("room-1"), any())).thenReturn(
                RoomMessageResponse.builder().id("m-1").content("hi").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RoomMessageCreateRequest.builder().content("hi").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("m-1"));

        verify(roomApiRateLimitService).checkRoomMessage("user-1", "room-1");
    }

    @Test
    void sendMessage_emptyContent_returns400WithMessageContentEmptyCode() throws Exception {
        when(roomMessageService.sendMessage(eq("room-1"), any())).thenThrow(new AppException(ErrorCode.MESSAGE_CONTENT_EMPTY));

        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RoomMessageCreateRequest.builder().content("  ").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.MESSAGE_CONTENT_EMPTY.getCode()));
    }

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/room-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RoomMessageCreateRequest.builder().content("hi").build())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roomMessageService);
    }
}
