package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.film.film_service.configuration.SecurityConfig;
import com.MyProject.film.film_service.dto.request.FilmRequest;
import com.MyProject.film.film_service.dto.request.RatingRequest;
import com.MyProject.film.film_service.dto.response.FilmAggregateResponse;
import com.MyProject.film.film_service.dto.response.FilmDetailResponse;
import com.MyProject.film.film_service.dto.response.FilmResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.dto.response.RatingLikeResponse;
import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.enums.FilmCategory;
import com.MyProject.film.film_service.enums.Genre;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.service.FilmApiRateLimitService;
import com.MyProject.film.film_service.service.FilmService;
import com.MyProject.film.film_service.service.RatingLikeService;
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

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FilmController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        FilmControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FilmControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean FilmService filmService;
    @MockitoBean FilmApiRateLimitService filmApiRateLimitService;
    @MockitoBean RatingLikeService ratingLikeService;
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

    private FilmRequest validFilmRequest() {
        return FilmRequest.builder()
                .title("Title").description("Desc")
                .directorIds(java.util.List.of("dir-1"))
                .country(Country.USA)
                .genres(Set.of(Genre.ACTION))
                .build();
    }

    @Test
    void createFilm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFilmRequest())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmService);
    }

    // JSR-250 @RolesAllowed("ADMIN") has no effect: @EnableMethodSecurity on SecurityConfig is missing
    // jsr250Enabled = true, so any authenticated (non-admin) user can currently reach this endpoint.
    @Test
    void createFilm_authenticatedNonAdmin_stillSucceeds_documentingRolesAllowedNoOpBug() throws Exception {
        when(filmService.createFilm(any())).thenReturn(FilmResponse.builder().id("film-1").title("Title").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFilmRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("film-1"));

        verify(filmApiRateLimitService).checkCreateFilm("user-1");
    }

    @Test
    void createFilm_blankTitle_returns400WithTitleRequiredCode() throws Exception {
        FilmRequest request = FilmRequest.builder()
                .title("  ").description("Desc")
                .directorIds(java.util.List.of("dir-1"))
                .country(Country.USA)
                .genres(Set.of(Genre.ACTION))
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.TITLE_REQUIRED.getCode()));

        verifyNoInteractions(filmService);
    }

    @Test
    void createFilm_missingDirectors_returns400WithDirectorRequiredCode() throws Exception {
        FilmRequest request = FilmRequest.builder()
                .title("Title").description("Desc")
                .directorIds(java.util.List.of())
                .country(Country.USA)
                .genres(Set.of(Genre.ACTION))
                .build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.DIRECTOR_REQUIRED.getCode()));
    }

    @Test
    void getPageFilms_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmService);
    }

    @Test
    void getPageFilms_authenticated_checksRateLimitAndReturnsPage() throws Exception {
        when(filmService.getPageFilms(1, 10)).thenReturn(
                PageResponse.<FilmSummaryResponse>builder().currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                        .data(java.util.List.of(FilmSummaryResponse.builder().id("film-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("film-1"));

        verify(filmApiRateLimitService).checkReadFilms("user-1");
    }

    @Test
    void updateFilm_filmNotFound_returns404WithFilmNotFoundCode() throws Exception {
        when(filmService.updateFilm(eq("missing"), any())).thenThrow(new AppException(ErrorCode.FILM_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.put("/missing")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFilmRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.FILM_NOT_FOUND.getCode()));
    }

    @Test
    void getAggregateFilms_authenticated_returnsAggregate() throws Exception {
        when(filmService.getAggregateFilms()).thenReturn(FilmAggregateResponse.builder().build());

        mockMvc.perform(MockMvcRequestBuilders.get("/aggregate").with(asUser("user-1")))
                .andExpect(status().isOk());
    }

    @Test
    void getFilmAggregate_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/film-1/aggregate"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmService);
    }

    @Test
    void getFilmAggregate_authenticated_returnsDetail() throws Exception {
        when(filmService.getFilm("film-1")).thenReturn(
                FilmDetailResponse.builder().film(FilmResponse.builder().id("film-1").build()).followed(false).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/film-1/aggregate").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.film.id").value("film-1"));
    }

    @Test
    void getFilmAggregate_notFound_returns404WithFilmNotFoundCode() throws Exception {
        when(filmService.getFilm("missing")).thenThrow(new AppException(ErrorCode.FILM_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/missing/aggregate").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.FILM_NOT_FOUND.getCode()));
    }

    @Test
    void browseFilms_missingRequiredCategory_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/browse").with(asUser("user-1")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(filmService);
    }

    @Test
    void browseFilms_authenticated_delegatesWithCategory() throws Exception {
        when(filmService.browseFilms(eq(FilmCategory.STANDALONE), any(), any(), any(), any(), eq(1), eq(10)))
                .thenReturn(PageResponse.<FilmSummaryResponse>builder().currentPage(1).pageSize(10).totalPages(0)
                        .totalElement(0L).data(java.util.List.of()).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/browse")
                        .param("category", "STANDALONE")
                        .with(asUser("user-1")))
                .andExpect(status().isOk());
    }

    @Test
    void searchFilms_missingTitleParam_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/search").with(asUser("user-1")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(filmService);
    }

    @Test
    void searchFilms_authenticated_returnsResults() throws Exception {
        when(filmService.searchFilms("matrix")).thenReturn(
                java.util.List.of(FilmSummaryResponse.builder().id("film-1").title("Matrix").build()));

        mockMvc.perform(MockMvcRequestBuilders.get("/search").param("title", "matrix").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result[0].title").value("Matrix"));
    }

    @Test
    void rateFilm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/film-1/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RatingRequest.builder().stars(5).build())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(filmService);
    }

    @Test
    void rateFilm_authenticated_checksRateLimitAndReturnsNewAverage() throws Exception {
        when(filmService.rateFilm(eq("film-1"), any())).thenReturn(4);

        mockMvc.perform(MockMvcRequestBuilders.post("/film-1/rating")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RatingRequest.builder().stars(4).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result").value(4));

        verify(filmApiRateLimitService).checkFilmRating("user-1", "film-1");
    }

    @Test
    void rateFilm_filmNotFound_returns404WithFilmNotFoundCode() throws Exception {
        when(filmService.rateFilm(eq("missing"), any())).thenThrow(new AppException(ErrorCode.FILM_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.post("/missing/rating")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RatingRequest.builder().stars(4).build())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.FILM_NOT_FOUND.getCode()));
    }

    @Test
    void toggleRatingLike_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/rating/rating-1/like"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ratingLikeService);
    }

    @Test
    void toggleRatingLike_authenticated_checksRateLimitAndReturnsResult() throws Exception {
        when(ratingLikeService.toggleLike("rating-1")).thenReturn(
                RatingLikeResponse.builder().liked(true).likeCount(3).build());

        mockMvc.perform(MockMvcRequestBuilders.post("/rating/rating-1/like").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.liked").value(true))
                .andExpect(jsonPath("result.likeCount").value(3));

        verify(filmApiRateLimitService).checkRatingLike("user-1", "rating-1");
    }

    @Test
    void toggleRatingLike_ratingNotFound_returns404WithRatingNotFoundCode() throws Exception {
        when(ratingLikeService.toggleLike("missing")).thenThrow(new AppException(ErrorCode.RATING_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.post("/rating/missing/like").with(asUser("user-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.RATING_NOT_FOUND.getCode()));
    }
}
