package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.film.film_service.configuration.SecurityConfig;
import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.service.DirectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(DirectorController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        DirectorControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DirectorControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean DirectorService directorService;
    // See ActorControllerTest for why every repository interface must be mocked here.
    @MockitoBean com.MyProject.film.film_service.repository.mysql.ActorRepository actorRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.DirectorRepository directorRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.EpisodeRepository episodeRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.FilmFollowRepository filmFollowRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.OutboxRepository outboxRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.RatingRepository ratingRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.FilmCastRepository filmCastRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.FilmRepository filmRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.FilmDirectorRepository filmDirectorRepository;
    @MockitoBean com.MyProject.film.film_service.repository.mysql.RatingLikeRepository ratingLikeRepository;
    @MockitoBean FilmElasticRepository filmElasticRepository;

    final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Test
    void createDirector_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/directors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DirectorRequest("D", null, null))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directorService);
    }

    @Test
    void createDirector_blankName_returns400WithNameRequiredCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/directors")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DirectorRequest("  ", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.NAME_REQUIRED.getCode()));

        verifyNoInteractions(directorService);
    }

    // Same JSR-250 no-op bug as ActorController - see ActorControllerTest for the root cause.
    @Test
    void createDirector_authenticatedNonAdmin_stillSucceeds_documentingRolesAllowedNoOpBug() throws Exception {
        when(directorService.createDirector(any())).thenReturn(DirectorResponse.builder().id("d-1").name("D").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/directors")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DirectorRequest("D", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("d-1"));
    }

    @Test
    void getAllDirectors_noAuthProvided_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/directors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllDirectors_authenticated_returnsPage() throws Exception {
        when(directorService.getAllDirectors(1, 10)).thenReturn(
                PageResponse.<DirectorResponse>builder().currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                        .data(java.util.List.of(DirectorResponse.builder().id("d-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/directors").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("d-1"));
    }

    @Test
    void getDirector_notFound_returns400WithDirectorNotFoundCode() throws Exception {
        when(directorService.getDirector("missing")).thenThrow(new AppException(ErrorCode.DIRECTOR_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/directors/missing").with(asUser("user-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.DIRECTOR_NOT_FOUND.getCode()));
    }

    @Test
    void updateDirector_authenticated_delegatesToService() throws Exception {
        when(directorService.updateDirector(eq("d-1"), any())).thenReturn(
                DirectorResponse.builder().id("d-1").name("Updated").build());

        mockMvc.perform(MockMvcRequestBuilders.put("/directors/d-1")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DirectorRequest("Updated", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.name").value("Updated"));
    }

    @Test
    void deleteDirector_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/directors/d-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(directorService).deleteDirector("d-1");
    }

    @Test
    void deleteDirector_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/directors/d-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directorService);
    }
}
