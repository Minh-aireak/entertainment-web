package com.MyProject.room_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.room_service.repository.httpclient.FilmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomFilmExternalServiceTest {

    @Mock FilmClient filmClient;

    RoomFilmExternalService roomFilmExternalService;

    @BeforeEach
    void setUp() {
        roomFilmExternalService = new RoomFilmExternalService(filmClient);
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
    void getFilmInfo_nullResponse_returnsEmptyOptional() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(null);

        assertThat(roomFilmExternalService.getFilmInfo("film-1")).isEmpty();
    }

    @Test
    void getFilmInfo_nullResult_returnsEmptyOptional() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(ApiResponse.<FilmClient.FilmAggregateInfo>builder().result(null).build());

        assertThat(roomFilmExternalService.getFilmInfo("film-1")).isEmpty();
    }

    @Test
    void getFilmInfo_nullFilmInsideAggregate_returnsEmptyOptional() {
        when(filmClient.getFilmAggregate("film-1")).thenReturn(
                ApiResponse.<FilmClient.FilmAggregateInfo>builder().result(new FilmClient.FilmAggregateInfo(null)).build());

        assertThat(roomFilmExternalService.getFilmInfo("film-1")).isEmpty();
    }

    @Test
    void getFilmInfoFallback_filmServiceDown_returnsEmptyOptionalInsteadOfThrowing() {
        Optional<FilmClient.FilmInfo> result = roomFilmExternalService.getFilmInfoFallback("film-1", new RuntimeException("film_service down"));

        assertThat(result).isEmpty();
    }
}
