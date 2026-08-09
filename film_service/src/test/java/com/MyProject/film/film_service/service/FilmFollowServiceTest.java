package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.dto.response.FileResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.FilmFollow;
import com.MyProject.film.film_service.entity.Outbox;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.FilmFollowMapper;
import com.MyProject.film.film_service.mapper.FilmMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.FilmFollowRepository;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilmFollowServiceTest {

    @Mock FilmFollowRepository filmFollowRepository;
    @Mock FilmRepository filmRepository;
    @Mock FilmFollowMapper filmFollowMapper;
    @Mock OutboxRepository outboxRepository;
    @Mock FilmMapper filmMapper;
    @Mock RedisService redisService;
    @Mock FileClient fileClient;

    FilmFollowService filmFollowService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filmFollowService = new FilmFollowService(filmFollowRepository, filmRepository, filmFollowMapper,
                outboxRepository, objectMapper, filmMapper, redisService, fileClient);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn("user-1");
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void processFollowAction_follow_persistsFollowAndEmitsOutboxEvent() {
        Film film = new Film();
        film.setId("film-1");
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1")).thenReturn(false);

        filmFollowService.processFollowAction("film-1", "FOLLOW");

        verify(filmFollowRepository).save(argThat(f -> f.getUserId().equals("user-1") && f.getFilm() == film));
        verify(outboxRepository).save(argThat((Outbox o) -> o.getTopic().equals("film.follow") && o.getPayload().contains("FOLLOW")));
        verify(redisService).delete("film:detail:film-1");
    }

    @Test
    void processFollowAction_followAlreadyFollowing_doesNotDuplicatePersistButStillEmitsEvent() {
        Film film = new Film();
        film.setId("film-1");
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1")).thenReturn(true);

        filmFollowService.processFollowAction("film-1", "FOLLOW");

        verify(filmFollowRepository, never()).save(any());
        verify(outboxRepository).save(any(Outbox.class));
    }

    @Test
    void processFollowAction_followFilmNotFound_throwsAndSkipsCacheInvalidation() {
        when(filmRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filmFollowService.processFollowAction("missing", "FOLLOW"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
        verifyNoInteractions(redisService);
    }

    @Test
    void processFollowAction_unfollow_removesFollowAndEmitsOutboxEvent() {
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1")).thenReturn(true);

        filmFollowService.processFollowAction("film-1", "UNFOLLOW");

        verify(filmFollowRepository).deleteByUserIdAndFilmId("user-1", "film-1");
        verify(outboxRepository).save(argThat((Outbox o) -> o.getPayload().contains("UNFOLLOW")));
    }

    @Test
    void processFollowAction_unfollowNotFollowing_skipsDeleteButStillEmitsEvent() {
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1")).thenReturn(false);

        filmFollowService.processFollowAction("film-1", "UNFOLLOW");

        verify(filmFollowRepository, never()).deleteByUserIdAndFilmId(any(), any());
        verify(outboxRepository).save(any(Outbox.class));
    }

    @Test
    void processFollowAction_unrecognizedAction_togglesBasedOnCurrentFollowState() {
        Film film = new Film();
        film.setId("film-1");
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1"))
                .thenReturn(false) // isFollowing() check inside processFollowAction
                .thenReturn(false); // existsBy... check inside followFilm()
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));

        filmFollowService.processFollowAction("film-1", "TOGGLE");

        verify(filmFollowRepository).save(any(FilmFollow.class));
    }

    @Test
    void getMyFollowedFilms_mapsAndResolvesThumbnails() {
        Film film = new Film();
        film.setId("film-1");
        FilmFollow follow = FilmFollow.builder().userId("user-1").film(film).build();
        when(filmFollowRepository.findAllByUserId("user-1")).thenReturn(List.of(follow));
        when(filmMapper.toFilmSummaryResponse(film)).thenReturn(
                FilmSummaryResponse.builder().id("film-1").thumbnailFileId("thumb-1").build());
        ApiResponse<FileResponse> apiResponse = ApiResponse.<FileResponse>builder()
                        .result(FileResponse.builder().url("https://cdn/thumb-1").build())
                        .build();
        when(fileClient.getFileInfo("thumb-1")).thenReturn(apiResponse);

        List<FilmSummaryResponse> result = filmFollowService.getMyFollowedFilms();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThumbnailUrl()).isEqualTo("https://cdn/thumb-1");
    }

    @Test
    void getMyFollowedFilms_emptyList_returnsEmpty() {
        when(filmFollowRepository.findAllByUserId("user-1")).thenReturn(List.of());

        List<FilmSummaryResponse> result = filmFollowService.getMyFollowedFilms();

        assertThat(result).isEmpty();
        verifyNoInteractions(fileClient);
    }

    @Test
    void getMyFollowedFilms_thumbnailResolveFails_returnsResponseWithoutThumbnailUrl() {
        Film film = new Film();
        film.setId("film-1");
        FilmFollow follow = FilmFollow.builder().userId("user-1").film(film).build();
        when(filmFollowRepository.findAllByUserId("user-1")).thenReturn(List.of(follow));
        when(filmMapper.toFilmSummaryResponse(film)).thenReturn(
                FilmSummaryResponse.builder().id("film-1").thumbnailFileId("thumb-1").build());
        when(fileClient.getFileInfo("thumb-1")).thenThrow(new RuntimeException("file-service down"));

        List<FilmSummaryResponse> result = filmFollowService.getMyFollowedFilms();

        assertThat(result.get(0).getThumbnailUrl()).isNull();
    }

    @Test
    void isFollowing_delegatesToRepository() {
        when(filmFollowRepository.existsByUserIdAndFilmId("user-1", "film-1")).thenReturn(true);

        assertThat(filmFollowService.isFollowing("film-1")).isTrue();
    }
}
