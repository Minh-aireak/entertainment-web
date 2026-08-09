package com.MyProject.film.film_service.controller;

import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.film.film_service.configuration.SecurityConfig;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.service.FilmApiRateLimitService;
import com.MyProject.film.film_service.service.FilmFollowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FilmFollowController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        FilmFollowControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FilmFollowControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean FilmFollowService filmFollowService;
    @MockitoBean FilmApiRateLimitService filmApiRateLimitService;
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

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Test
    void processFollowAction_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/follows/film-1/FOLLOW"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmFollowService);
    }

    @Test
    void processFollowAction_authenticated_checksRateLimitThenDelegates() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/follows/film-1/FOLLOW").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(filmApiRateLimitService).checkFilmFollow("user-1", "film-1");
        verify(filmFollowService).processFollowAction("film-1", "FOLLOW");
    }

    @Test
    void processFollowAction_filmNotFound_returns404WithFilmNotFoundCode() throws Exception {
        doThrow(new AppException(ErrorCode.FILM_NOT_FOUND))
                .when(filmFollowService).processFollowAction("missing", "FOLLOW");

        mockMvc.perform(MockMvcRequestBuilders.post("/follows/missing/FOLLOW").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.FILM_NOT_FOUND.getCode()));
    }

    @Test
    void getMyFollowedFilms_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/follows/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmFollowService);
    }

    @Test
    void getMyFollowedFilms_authenticated_returnsList() throws Exception {
        when(filmFollowService.getMyFollowedFilms()).thenReturn(
                java.util.List.of(FilmSummaryResponse.builder().id("film-1").title("Title").build()));

        mockMvc.perform(MockMvcRequestBuilders.get("/follows/my").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result[0].id").value("film-1"));
    }

    @Test
    void getMyFollowedFilms_noFollows_returnsEmptyList() throws Exception {
        when(filmFollowService.getMyFollowedFilms()).thenReturn(java.util.List.of());

        mockMvc.perform(MockMvcRequestBuilders.get("/follows/my").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result").isArray())
                .andExpect(jsonPath("result").isEmpty());
    }
}
