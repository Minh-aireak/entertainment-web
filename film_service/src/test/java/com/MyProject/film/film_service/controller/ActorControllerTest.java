package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.film.film_service.configuration.SecurityConfig;
import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.service.ActorService;
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

@WebMvcTest(ActorController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        ActorControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActorControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean ActorService actorService;
    // FilmServiceApplication declares @EnableJpaRepositories/@EnableElasticsearchRepositories directly
    // on the primary source class, so Spring processes them (and tries to build real repository
    // proxies backed by an entityManagerFactory/ElasticsearchOperations bean) regardless of which
    // controller is under test in this @WebMvcTest slice - every repository interface must be mocked
    // to avoid ApplicationContext startup failure.
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
    void createActor_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/actors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActorRequest("A", null, null))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actorService);
    }

    @Test
    void createActor_blankName_returns400WithNameRequiredCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/actors")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActorRequest("  ", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.NAME_REQUIRED.getCode()));

        verifyNoInteractions(actorService);
    }

    // JSR-250 @RolesAllowed("ADMIN") has no effect in this app: @EnableMethodSecurity on SecurityConfig
    // is missing jsr250Enabled = true, so any authenticated (non-admin) user can currently reach this
    // endpoint. This test documents the actual (buggy) behavior rather than the intended one.
    @Test
    void createActor_authenticatedNonAdmin_stillSucceeds_documentingRolesAllowedNoOpBug() throws Exception {
        when(actorService.createActor(any())).thenReturn(ActorResponse.builder().id("a-1").name("A").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/actors")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActorRequest("A", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("a-1"));
    }

    @Test
    void getAllActors_noAuthProvided_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllActors_authenticated_returnsPage() throws Exception {
        when(actorService.getAllActors(1, 10)).thenReturn(
                PageResponse.<ActorResponse>builder().currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                        .data(java.util.List.of(ActorResponse.builder().id("a-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/actors").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("a-1"));
    }

    @Test
    void getActor_notFound_returns400WithActorNotFoundCode() throws Exception {
        when(actorService.getActor("missing")).thenThrow(new AppException(ErrorCode.ACTOR_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/actors/missing").with(asUser("user-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.ACTOR_NOT_FOUND.getCode()));
    }

    @Test
    void updateActor_authenticated_delegatesToService() throws Exception {
        when(actorService.updateActor(eq("a-1"), any())).thenReturn(
                ActorResponse.builder().id("a-1").name("Updated").build());

        mockMvc.perform(MockMvcRequestBuilders.put("/actors/a-1")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ActorRequest("Updated", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.name").value("Updated"));
    }

    @Test
    void deleteActor_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/actors/a-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(actorService).deleteActor("a-1");
    }

    @Test
    void deleteActor_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/actors/a-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actorService);
    }
}
