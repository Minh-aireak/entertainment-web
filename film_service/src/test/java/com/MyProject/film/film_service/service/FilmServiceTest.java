package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.document.FilmDoc;
import com.MyProject.film.film_service.dto.request.FilmRequest;
import com.MyProject.film.film_service.dto.request.RatingRequest;
import com.MyProject.film.film_service.dto.response.FilmDetailResponse;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.dto.response.FileResponse;
import com.MyProject.film.film_service.dto.response.FilmCastResponse;
import com.MyProject.film.film_service.dto.response.FilmDirectorResponse;
import com.MyProject.film.film_service.dto.response.FilmResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.entity.Actor;
import com.MyProject.film.film_service.entity.Director;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.Rating;
import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.enums.FilmCategory;
import com.MyProject.film.film_service.enums.FilmSortField;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.mapper.FilmMapper;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilmServiceTest {

    private static final String USER_ID = "user-1";

    @Mock FilmRepository filmRepository;
    @Mock FilmMapper filmMapper;
    @Mock com.MyProject.common.redis.RedisService redisService;
    @Mock OutboxRepository outboxRepository;
    @Mock RatingRepository ratingRepository;
    @Mock EpisodeRepository episodeRepository;
    @Mock DirectorRepository directorRepository;
    @Mock ActorRepository actorRepository;
    @Mock FilmCastRepository filmCastRepository;
    @Mock FilmDirectorRepository filmDirectorRepository;
    @Mock FilmElasticRepository filmElasticRepository;
    @Mock FilmFollowService filmFollowService;
    @Mock CommentExternalService commentExternalService;
    @Mock FileClient fileClient;

    FilmService filmService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        filmService = new FilmService(filmRepository, filmMapper, redisService, outboxRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()), ratingRepository, episodeRepository,
                directorRepository, actorRepository, filmCastRepository, filmDirectorRepository,
                filmElasticRepository, filmFollowService, commentExternalService, fileClient);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

        lenient().when(filmMapper.toFilmResponse(any(Film.class))).thenAnswer(inv -> {
            Film f = inv.getArgument(0);
            return FilmResponse.builder().id(f.getId()).title(f.getTitle())
                    .thumbnailFileId(f.getThumbnailFileId()).averageRating(f.getAverageRating())
                    .ratingCount(f.getRatingCount()).build();
        });
        lenient().when(filmRepository.save(any(Film.class))).thenAnswer(inv -> {
            Film f = inv.getArgument(0);
            if (f.getId() == null) f.setId("film-" + System.nanoTime());
            return f;
        });
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    // ---------- rateFilm ----------

    @Test
    void rateFilm_filmNotFound_throws() {
        when(filmRepository.findById("film-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filmService.rateFilm("film-1", RatingRequest.builder().stars(5).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
    }

    @Test
    void rateFilm_newRating_savesRatingAndPublishesEventWithOldStarsZero() {
        Film film = new Film();
        film.setId("film-1");
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(ratingRepository.findByFilmAndUserId(film, USER_ID)).thenReturn(Optional.empty());

        Integer stars = filmService.rateFilm("film-1", RatingRequest.builder().stars(4).build());

        assertThat(stars).isEqualTo(4);
        verify(ratingRepository).save(argThat(r -> r.getStars() == 4 && r.getUserId().equals(USER_ID)));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("film.rating")
                && o.getPayload().contains("\"oldStars\":0")));
    }

    @Test
    void rateFilm_existingRating_updatesStarsAndPublishesEventWithOldStars() {
        Film film = new Film();
        film.setId("film-1");
        Rating existing = Rating.builder().id("r-1").film(film).userId(USER_ID).stars(2).build();
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));
        when(ratingRepository.findByFilmAndUserId(film, USER_ID)).thenReturn(Optional.of(existing));

        filmService.rateFilm("film-1", RatingRequest.builder().stars(5).build());

        assertThat(existing.getStars()).isEqualTo(5);
        verify(ratingRepository).save(existing);
        verify(outboxRepository).save(argThat(o -> o.getPayload().contains("\"oldStars\":2")));
    }

    // ---------- browseFilms ----------

    @Test
    void browseFilms_series_queriesSeriesOrStandaloneWithTrue() {
        Page<Film> page = new PageImpl<>(List.of());
        when(filmRepository.findSeriesOrStandalone(eq(true), eq(Genre.ANIMATION), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        filmService.browseFilms(FilmCategory.SERIES, null, null, FilmSortField.LAST_UPDATE, Sort.Direction.DESC, 1, 10);

        verify(filmRepository).findSeriesOrStandalone(eq(true), eq(Genre.ANIMATION), any(), any(), any(Pageable.class));
        verify(filmRepository, never()).findByGenreContaining(any(), any(), any());
    }

    @Test
    void browseFilms_standalone_queriesSeriesOrStandaloneWithFalse() {
        Page<Film> page = new PageImpl<>(List.of());
        when(filmRepository.findSeriesOrStandalone(eq(false), eq(Genre.ANIMATION), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        filmService.browseFilms(FilmCategory.STANDALONE, Country.USA, null, FilmSortField.RELEASE_DATE, Sort.Direction.ASC, 1, 10);

        verify(filmRepository).findSeriesOrStandalone(eq(false), eq(Genre.ANIMATION), eq(Country.USA), any(), any(Pageable.class));
    }

    @Test
    void browseFilms_animation_queriesByGenreContaining() {
        Page<Film> page = new PageImpl<>(List.of());
        when(filmRepository.findByGenreContaining(eq(Genre.ANIMATION), any(), any(Pageable.class))).thenReturn(page);

        filmService.browseFilms(FilmCategory.ANIMATION, null, null, FilmSortField.FOLLOW_COUNT, Sort.Direction.DESC, 1, 10);

        verify(filmRepository).findByGenreContaining(eq(Genre.ANIMATION), any(), any(Pageable.class));
        verify(filmRepository, never()).findSeriesOrStandalone(anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void searchFilms_legacyElasticsearchStatuses_areMappedToCurrentStatuses() {
        when(filmElasticRepository.findByTitleContaining("film")).thenReturn(List.of(
                FilmDoc.builder().id("film-1").title("Playing film").status("NOW_PLAYING").build(),
                FilmDoc.builder().id("film-2").title("Ended film").status("ENDED").build()
        ));

        List<FilmSummaryResponse> result = filmService.searchFilms("film");

        assertThat(result).extracting(FilmSummaryResponse::getStatus)
                .containsExactly(FilmStatus.ONGOING, FilmStatus.COMPLETED);
    }

    // ---------- createFilm ----------

    private FilmRequest.FilmRequestBuilder filmRequestBuilder() {
        return FilmRequest.builder().title("New Film").description("desc")
                .country(Country.USA).genres(java.util.Set.of(Genre.ACTION))
                .directorIds(List.of("dir-1"));
    }

    @Test
    void createFilm_directorNotFound_throws() {
        when(filmMapper.toFilm(any(FilmRequest.class))).thenReturn(new Film());
        when(directorRepository.findById("dir-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filmService.createFilm(filmRequestBuilder().build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIRECTOR_NOT_FOUND);
    }

    @Test
    void createFilm_actorNotFound_throws() {
        when(filmMapper.toFilm(any(FilmRequest.class))).thenReturn(new Film());
        when(directorRepository.findById("dir-1")).thenReturn(Optional.of(new Director()));
        when(actorRepository.findById("actor-1")).thenReturn(Optional.empty());
        FilmRequest request = filmRequestBuilder()
                .casts(List.of(FilmRequest.CastRequest.builder().actorId("actor-1").build())).build();

        assertThatThrownBy(() -> filmService.createFilm(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_FOUND);
    }

    @Test
    void createFilm_happyPath_savesDirectorsCastsAndSyncsElasticsearch() {
        when(filmMapper.toFilm(any(FilmRequest.class))).thenReturn(new Film());
        Director director = new Director();
        director.setId("dir-1");
        when(directorRepository.findById("dir-1")).thenReturn(Optional.of(director));
        Actor actor = new Actor();
        actor.setId("actor-1");
        when(actorRepository.findById("actor-1")).thenReturn(Optional.of(actor));
        FilmRequest request = filmRequestBuilder()
                .casts(List.of(FilmRequest.CastRequest.builder().actorId("actor-1").characterName("Hero").build()))
                .build();

        FilmResponse response = filmService.createFilm(request);

        assertThat(response.getId()).isNotBlank();
        verify(filmDirectorRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(filmCastRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("film.sync")));
    }

    @Test
    void invalidateFilmCaches_removesAllFilmSummaryCaches() {
        filmService.invalidateFilmCaches("film-1");

        verify(redisService).delete("film:detail:film-1");
        verify(redisService).deletePattern("film:comments:film-1:*");
        verify(redisService).deletePattern("film:latest:page:*");
        verify(redisService).deletePattern("film:hot:page:*");
        verify(redisService).deletePattern("film:ongoing:page:*");
    }

    // ---------- getFilm ----------

    @Test
    void getFilm_notFoundAndCacheMiss_throwsFilmNotFound() {
        when(redisService.get(eq("film:detail:missing"), any())).thenReturn(null);
        when(filmRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filmService.getFilm("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILM_NOT_FOUND);
    }

    @Test
    void getFilm_cacheHit_skipsDbFetchAndAddsPersonalization() {
        FilmResponse cached = FilmResponse.builder().id("film-1").title("Cached Film").build();
        when(redisService.get(eq("film:detail:film-1"), any())).thenReturn(cached);
        when(redisService.get(eq("film:comments:film-1:page:1"), any())).thenReturn(null);
        when(commentExternalService.getComments("film-1", 1, 10))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.<com.MyProject.film.film_service.dto.response.CommentResponse>builder().data(List.of()).build()));
        when(filmFollowService.isFollowing("film-1")).thenReturn(true);
        when(ratingRepository.findByFilmIdAndUserId("film-1", USER_ID)).thenReturn(
                Optional.of(Rating.builder().stars(3).build()));

        FilmDetailResponse response = filmService.getFilm("film-1");

        assertThat(response.getFilm().getTitle()).isEqualTo("Cached Film");
        assertThat(response.getUserRating()).isEqualTo(3);
        assertThat(response.getFollowed()).isTrue();
        verify(filmRepository, never()).findById(any());
    }

    @Test
    void getFilm_resolvesActorAndDirectorAvatarsFromFileIds() {
        ActorResponse actor = ActorResponse.builder()
                .id("actor-1")
                .avatarFileId("movie-platform/actor.png")
                .build();
        DirectorResponse director = DirectorResponse.builder()
                .id("director-1")
                .avatarFileId("movie-platform/director.png")
                .build();
        FilmResponse cached = FilmResponse.builder()
                .id("film-1")
                .casts(List.of(FilmCastResponse.builder().id("cast-1").actor(actor).build()))
                .directors(List.of(FilmDirectorResponse.builder().id("film-director-1").director(director).build()))
                .build();
        when(redisService.get(eq("film:detail:film-1"), any())).thenReturn(cached);
        when(redisService.get(eq("film:comments:film-1:page:1"), any())).thenReturn(null);
        when(commentExternalService.getComments("film-1", 1, 10))
                .thenReturn(CompletableFuture.completedFuture(PageResponse.<com.MyProject.film.film_service.dto.response.CommentResponse>builder().data(List.of()).build()));
        when(fileClient.getFileInfo("movie-platform/actor.png")).thenReturn(
                ApiResponse.<FileResponse>builder()
                        .result(FileResponse.builder().url("https://cdn/actor.png").build())
                        .build());
        when(fileClient.getFileInfo("movie-platform/director.png")).thenReturn(
                ApiResponse.<FileResponse>builder()
                        .result(FileResponse.builder().url("https://cdn/director.png").build())
                        .build());
        when(ratingRepository.findByFilmIdAndUserId("film-1", USER_ID)).thenReturn(Optional.empty());

        FilmDetailResponse response = filmService.getFilm("film-1");

        assertThat(response.getFilm().getCasts().get(0).getActor().getAvatarUrl())
                .isEqualTo("https://cdn/actor.png");
        assertThat(response.getFilm().getDirectors().get(0).getDirector().getAvatarUrl())
                .isEqualTo("https://cdn/director.png");
    }
}
