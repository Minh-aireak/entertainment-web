package com.MyProject.film.film_service.controller;

import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.film.film_service.configuration.SecurityConfig;
import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.service.EpisodeService;
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

@WebMvcTest(EpisodeController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        EpisodeControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EpisodeControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean EpisodeService episodeService;
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
    void getEpisodesByFilm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/episodes/film/film-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(episodeService);
    }

    @Test
    void getEpisodesByFilm_authenticated_returnsList() throws Exception {
        when(episodeService.getEpisodesByFilm("film-1")).thenReturn(
                java.util.List.of(EpisodeResponse.builder().id("e-1").filmId("film-1").build()));

        mockMvc.perform(MockMvcRequestBuilders.get("/episodes/film/film-1").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result[0].id").value("e-1"));
    }

    @Test
    void getEpisodesByFilm_filmNotFound_returns404WithFilmNotFoundCode() throws Exception {
        when(episodeService.getEpisodesByFilm("missing")).thenThrow(new AppException(ErrorCode.FILM_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/episodes/film/missing").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.FILM_NOT_FOUND.getCode()));
    }

    @Test
    void createEpisode_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/episodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EpisodeRequest.builder().filmId("film-1").build())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(episodeService);
    }

    // Same JSR-250 @RolesAllowed no-op bug as ActorController - see ActorControllerTest for root cause.
    @Test
    void createEpisode_authenticatedNonAdmin_stillSucceeds_documentingRolesAllowedNoOpBug() throws Exception {
        when(episodeService.createEpisode(any())).thenReturn(EpisodeResponse.builder().id("e-1").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/episodes")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EpisodeRequest.builder().filmId("film-1").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("e-1"));
    }

    @Test
    void updateEpisode_notFound_returns400WithEpisodeNotFoundCode() throws Exception {
        when(episodeService.updateEpisode(eq("missing"), any())).thenThrow(new AppException(ErrorCode.EPISODE_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.put("/episodes/missing")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EpisodeRequest.builder().filmId("film-1").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.EPISODE_NOT_FOUND.getCode()));
    }

    @Test
    void deleteEpisode_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/episodes/e-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(episodeService).deleteEpisode("e-1");
    }

    @Test
    void deleteEpisode_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/episodes/e-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(episodeService);
    }
}
