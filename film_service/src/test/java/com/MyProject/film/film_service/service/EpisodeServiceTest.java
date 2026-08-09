package com.MyProject.film.film_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
import com.MyProject.film.film_service.entity.Episode;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.FilmFollow;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.mapper.EpisodeMapper;
import com.MyProject.film.film_service.repository.mysql.EpisodeRepository;
import com.MyProject.film.film_service.repository.mysql.FilmFollowRepository;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class EpisodeServiceTest {

    @Mock EpisodeRepository episodeRepository;
    @Mock FilmRepository filmRepository;
    @Mock FilmFollowRepository filmFollowRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock EpisodeMapper episodeMapper;
    @Mock FilmService filmService;

    EpisodeService episodeService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        episodeService = new EpisodeService(episodeRepository, filmRepository, filmFollowRepository,
                outboxRepository, episodeMapper, new ObjectMapper(), filmService);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn("user-1");

        lenient().when(episodeMapper.toEpisode(any(EpisodeRequest.class))).thenAnswer(inv -> {
            EpisodeRequest req = inv.getArgument(0);
            Episode e = new Episode();
            e.setSeasonNumber(req.getSeasonNumber());
            e.setEpisodeNumber(req.getEpisodeNumber());
            e.setTitle(req.getTitle());
            e.setVideoFileId(req.getVideoFileId());
            e.setDurationMinutes(req.getDurationMinutes());
            return e;
        });
        lenient().when(episodeMapper.toEpisodeResponse(any(Episode.class))).thenAnswer(inv -> {
            Episode e = inv.getArgument(0);
            return EpisodeResponse.builder().id(e.getId()).title(e.getTitle())
                    .seasonNumber(e.getSeasonNumber()).episodeNumber(e.getEpisodeNumber())
                    .filmId(e.getFilm() != null ? e.getFilm().getId() : null).build();
        });
        lenient().when(episodeRepository.save(any(Episode.class))).thenAnswer(inv -> {
            Episode e = inv.getArgument(0);
            if (e.getId() == null) e.setId("ep-" + System.nanoTime());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    // ---------- getEpisodesByFilm ----------

    @Test
    void getEpisodesByFilm_filmNotFound_throws() {
        when(filmRepository.existsById("film-1")).thenReturn(false);

        assertThatThrownBy(() -> episodeService.getEpisodesByFilm("film-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
    }

    @Test
    void getEpisodesByFilm_happyPath_returnsMappedList() {
        when(filmRepository.existsById("film-1")).thenReturn(true);
        Episode episode = new Episode();
        episode.setId("ep-1");
        when(episodeRepository.findByFilm_IdOrderBySeasonNumberAscEpisodeNumberAsc("film-1"))
                .thenReturn(List.of(episode));

        List<EpisodeResponse> result = episodeService.getEpisodesByFilm("film-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("ep-1");
    }

    // ---------- createEpisode ----------

    @Test
    void createEpisode_filmNotFound_throws() {
        when(filmRepository.findById("film-1")).thenReturn(Optional.empty());
        EpisodeRequest request = EpisodeRequest.builder().filmId("film-1").title("Ep 1").build();

        assertThatThrownBy(() -> episodeService.createEpisode(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
    }

    @Test
    void createEpisode_happyPath_incrementsEpisodeCountAndNotifiesFollowers() {
        Film film = new Film();
        film.setId("film-1");
        film.setTitle("Some Film");
        film.setEpisodeCount(5);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(filmFollowRepository.findAllByFilmId("film-1")).thenReturn(
                List.of(FilmFollow.builder().userId("follower-1").build(), FilmFollow.builder().userId("follower-2").build()));
        EpisodeRequest request = EpisodeRequest.builder().filmId("film-1").title("Ep 6").build();

        episodeService.createEpisode(request);

        assertThat(film.getEpisodeCount()).isEqualTo(6);
        verify(filmRepository).save(film);
        verify(filmService).syncFilmToElasticsearch(film);
        verify(outboxRepository).save(argThat(o -> o.getPayload().contains("NEW_EPISODE")
                && o.getPayload().contains("follower-1")));
    }

    @Test
    void createEpisode_noFollowers_doesNotPublishNotification() {
        Film film = new Film();
        film.setId("film-1");
        film.setEpisodeCount(0);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(filmFollowRepository.findAllByFilmId("film-1")).thenReturn(List.of());
        EpisodeRequest request = EpisodeRequest.builder().filmId("film-1").title("Ep 1").build();

        episodeService.createEpisode(request);

        verifyNoInteractions(outboxRepository);
    }

    // ---------- updateEpisode ----------

    @Test
    void updateEpisode_notFound_throws() {
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> episodeService.updateEpisode("ep-1", EpisodeRequest.builder().build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EPISODE_NOT_FOUND);
    }

    @Test
    void updateEpisode_sameFilm_doesNotChangeEpisodeCounts() {
        Film film = new Film();
        film.setId("film-1");
        film.setEpisodeCount(3);
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setFilm(film);
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.of(episode));
        EpisodeRequest request = EpisodeRequest.builder().filmId("film-1").title("Updated").build();

        episodeService.updateEpisode("ep-1", request);

        verify(filmRepository, never()).save(any());
        verify(filmService, never()).syncFilmToElasticsearch(any());
    }

    @Test
    void updateEpisode_movedToNewFilm_decrementsOldIncrementsNew() {
        Film oldFilm = new Film();
        oldFilm.setId("old-film");
        oldFilm.setEpisodeCount(2);
        Film newFilm = new Film();
        newFilm.setId("new-film");
        newFilm.setEpisodeCount(4);
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setFilm(oldFilm);
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.of(episode));
        when(filmRepository.findById("new-film")).thenReturn(Optional.of(newFilm));
        EpisodeRequest request = EpisodeRequest.builder().filmId("new-film").title("Moved").build();

        episodeService.updateEpisode("ep-1", request);

        assertThat(oldFilm.getEpisodeCount()).isEqualTo(1);
        assertThat(newFilm.getEpisodeCount()).isEqualTo(5);
        assertThat(episode.getFilm()).isEqualTo(newFilm);
        verify(filmService).syncFilmToElasticsearch(oldFilm);
        verify(filmService).syncFilmToElasticsearch(newFilm);
    }

    @Test
    void updateEpisode_movedToMissingFilm_throwsFilmNotFound() {
        Film oldFilm = new Film();
        oldFilm.setId("old-film");
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setFilm(oldFilm);
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.of(episode));
        when(filmRepository.findById("missing-film")).thenReturn(Optional.empty());
        EpisodeRequest request = EpisodeRequest.builder().filmId("missing-film").build();

        assertThatThrownBy(() -> episodeService.updateEpisode("ep-1", request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
    }

    // ---------- deleteEpisode ----------

    @Test
    void deleteEpisode_notFound_throws() {
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> episodeService.deleteEpisode("ep-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EPISODE_NOT_FOUND);
    }

    @Test
    void deleteEpisode_happyPath_decrementsFilmEpisodeCount() {
        Film film = new Film();
        film.setId("film-1");
        film.setEpisodeCount(3);
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setFilm(film);
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.of(episode));

        episodeService.deleteEpisode("ep-1");

        assertThat(film.getEpisodeCount()).isEqualTo(2);
        verify(episodeRepository).delete(episode);
        verify(filmService).syncFilmToElasticsearch(film);
    }

    @Test
    void deleteEpisode_episodeCountAlreadyZero_clampsAtZero() {
        Film film = new Film();
        film.setId("film-1");
        film.setEpisodeCount(0);
        Episode episode = new Episode();
        episode.setId("ep-1");
        episode.setFilm(film);
        when(episodeRepository.findById("ep-1")).thenReturn(Optional.of(episode));

        episodeService.deleteEpisode("ep-1");

        assertThat(film.getEpisodeCount()).isZero();
    }
}
