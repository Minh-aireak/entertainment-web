package com.MyProject.room_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.repository.httpclient.FilmClient;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomFilmExternalServiceTest {

    @Mock FilmClient filmClient;
    @Mock RedisService redisService;

    RoomFilmExternalService roomFilmExternalService;

    @BeforeEach
    void setUp() {
        roomFilmExternalService = new RoomFilmExternalService(filmClient, redisService);
        lenient().when(redisService.get(any(), any())).thenReturn(null);
    }

    @Test
    void getFilmInfo_happyPath_returnsFilmInfo() {
        FilmClient.FilmInfo filmInfo = new FilmClient.FilmInfo("film-1", "Title", "thumb.png");
        FilmClient.FilmAggregateInfo aggregate = new FilmClient.FilmAggregateInfo(filmInfo);
        when(filmClient.getFilmAggregate("film-1")).thenReturn(ApiResponse.<FilmClient.FilmAggregateInfo>builder().result(aggregate).build());

        Optional<FilmClient.FilmInfo> result = roomFilmExternalService.getFilmInfo("film-1");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Title");
    }

    @Test
    void getFilmInfo_nullResponse_throwsServiceUnavailable() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(null);

        assertThatThrownBy(() -> roomFilmExternalService.getFilmInfo("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    @Test
    void getFilmInfo_nullResult_throwsServiceUnavailable() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(ApiResponse.<FilmClient.FilmAggregateInfo>builder().result(null).build());

        assertThatThrownBy(() -> roomFilmExternalService.getFilmInfo("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    @Test
    void getFilmInfo_nullFilmInsideAggregate_throwsServiceUnavailable() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(
                ApiResponse.<FilmClient.FilmAggregateInfo>builder().result(new FilmClient.FilmAggregateInfo(null)).build());

        assertThatThrownBy(() -> roomFilmExternalService.getFilmInfo("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    @Test
    void getFilmInfo_structuredFilmNotFound_throwsInvalidFilm() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(notFound.contentUTF8()).thenReturn("{\"code\":8903,\"message\":\"Film not found!\"}");
        when(filmClient.getFilmAggregate("film-1")).thenThrow(notFound);

        assertThatThrownBy(() -> roomFilmExternalService.getFilmInfo("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILM);
    }

    @Test
    void getFilmInfoFallback_filmServiceDown_throwsServiceUnavailable() {
        assertThatThrownBy(() -> roomFilmExternalService.getFilmInfoFallback("film-1", new RuntimeException("film_service down")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    // ---------- getEpisodesByFilm ----------

    @Test
    void getEpisodesByFilm_cacheMiss_fetchesAndCachesNonEmptyResult() {
        List<FilmClient.EpisodeInfo> episodes = List.of(new FilmClient.EpisodeInfo("ep-1", "film-1", 20));
        when(filmClient.getEpisodesByFilm("film-1")).thenReturn(ApiResponse.<List<FilmClient.EpisodeInfo>>builder().result(episodes).build());

        List<FilmClient.EpisodeInfo> result = roomFilmExternalService.getEpisodesByFilm("film-1");

        assertThat(result).containsExactlyElementsOf(episodes);
        verify(redisService).setWithExpiration(any(), eq(episodes), anyLong(), any());
    }

    @Test
    void getEpisodesByFilm_cacheHit_doesNotCallFilmClient() {
        List<FilmClient.EpisodeInfo> cached = List.of(new FilmClient.EpisodeInfo("ep-1", "film-1", 20));
        when(redisService.get(any(), any())).thenReturn(cached);

        List<FilmClient.EpisodeInfo> result = roomFilmExternalService.getEpisodesByFilm("film-1");

        assertThat(result).containsExactlyElementsOf(cached);
        verify(filmClient, never()).getEpisodesByFilm(any());
    }

    @Test
    void getEpisodesByFilm_emptyResult_isNotCached() {
        when(filmClient.getEpisodesByFilm("film-1")).thenReturn(ApiResponse.<List<FilmClient.EpisodeInfo>>builder().result(List.of()).build());

        List<FilmClient.EpisodeInfo> result = roomFilmExternalService.getEpisodesByFilm("film-1");

        assertThat(result).isEmpty();
        verify(redisService, never()).setWithExpiration(any(), any(), anyLong(), any());
    }

    @Test
    void fetchEpisodesByFilm_structuredFilmNotFound_throwsInvalidFilm() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(notFound.contentUTF8()).thenReturn("{\"code\":8903,\"message\":\"Film not found!\"}");
        when(filmClient.getEpisodesByFilm("film-1")).thenThrow(notFound);

        assertThatThrownBy(() -> roomFilmExternalService.fetchEpisodesByFilm("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILM);
    }

    @Test
    void fetchEpisodesByFilm_unstructuredNotFound_throwsFilmServiceUnavailable() {
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        when(notFound.contentUTF8()).thenReturn("<!doctype html><title>HTTP Status 404 - Not Found</title>");
        when(filmClient.getEpisodesByFilm("film-1")).thenThrow(notFound);

        assertThatThrownBy(() -> roomFilmExternalService.fetchEpisodesByFilm("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    @Test
    void getEpisodesByFilmFallback_filmServiceDown_throwsServiceUnavailable() {
        assertThatThrownBy(() -> roomFilmExternalService.getEpisodesByFilmFallback("film-1", new RuntimeException("down")))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILM_SERVICE_UNAVAILABLE);
    }

    @Test
    void getEpisodesByFilmFallback_businessError_rethrowsIt() {
        AppException invalidFilm = new AppException(ErrorCode.INVALID_FILM);

        assertThatThrownBy(() -> roomFilmExternalService.getEpisodesByFilmFallback("film-1", invalidFilm))
                .isSameAs(invalidFilm);
    }

    @Test
    void getCachedEpisode_matchFound_returnsIt() {
        List<FilmClient.EpisodeInfo> cached = List.of(
                new FilmClient.EpisodeInfo("ep-1", "film-1", 20),
                new FilmClient.EpisodeInfo("ep-2", "film-1", 25));
        when(redisService.get(any(), any())).thenReturn(cached);

        Optional<FilmClient.EpisodeInfo> result = roomFilmExternalService.getCachedEpisode("film-1", "ep-2");

        assertThat(result).isPresent();
        assertThat(result.get().getDurationMinutes()).isEqualTo(25);
    }

    @Test
    void getCachedEpisode_cacheMiss_returnsEmptyWithoutCallingFilmClient() {
        Optional<FilmClient.EpisodeInfo> result = roomFilmExternalService.getCachedEpisode("film-1", "ep-1");

        assertThat(result).isEmpty();
        verify(filmClient, never()).getEpisodesByFilm(any());
    }

    @Test
    void getCachedEpisode_redisThrows_returnsEmptyInsteadOfThrowing() {
        doThrow(new RuntimeException("redis down")).when(redisService).get(any(), any());

        Optional<FilmClient.EpisodeInfo> result = roomFilmExternalService.getCachedEpisode("film-1", "ep-1");

        assertThat(result).isEmpty();
    }
}
