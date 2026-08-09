package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.repository.mysql.FilmRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilmRatingServiceKafkaTest {

    @Mock FilmRepository filmRepository;
    @Mock FilmService filmService;
    @Mock RedisService redisService;
    @Mock Acknowledgment ack;

    FilmRatingServiceKafka filmRatingServiceKafka;

    @BeforeEach
    void setUp() {
        filmRatingServiceKafka = new FilmRatingServiceKafka(filmRepository, new ObjectMapper(), filmService, redisService);
    }

    private String eventPayload(String eventId, String filmId, int stars, int oldStars) {
        return "{\"eventId\":\"" + eventId + "\",\"filmId\":\"" + filmId + "\",\"userId\":\"user-1\",\"stars\":"
                + stars + ",\"oldStars\":" + oldStars + "}";
    }

    @Test
    void listenRatingEvent_newRating_incrementsCountAndRecomputesAverage() {
        Film film = new Film();
        film.setId("film-1");
        film.setAverageRating(4.0);
        film.setRatingCount(1);
        when(redisService.getAsString(anyString())).thenReturn(null);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));

        filmRatingServiceKafka.listenRatingEvent(eventPayload("e-1", "film-1", 2, 0), ack);

        assertThat(film.getRatingCount()).isEqualTo(2);
        assertThat(film.getAverageRating()).isCloseTo(3.0, within(0.0001));
        verify(filmRepository).save(film);
        verify(filmService).syncFilmToElasticsearch(film);
        verify(ack).acknowledge();
    }

    @Test
    void listenRatingEvent_updateExistingRating_keepsCountButRecomputesAverage() {
        Film film = new Film();
        film.setId("film-1");
        film.setAverageRating(3.0);
        film.setRatingCount(2);
        when(redisService.getAsString(anyString())).thenReturn(null);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));

        filmRatingServiceKafka.listenRatingEvent(eventPayload("e-1", "film-1", 5, 1), ack);

        assertThat(film.getRatingCount()).isEqualTo(2);
        assertThat(film.getAverageRating()).isCloseTo(5.0, within(0.0001));
        verify(ack).acknowledge();
    }

    @Test
    void listenRatingEvent_filmNotFound_skipsUpdateButStillMarksProcessedAndAcks() {
        when(redisService.getAsString(anyString())).thenReturn(null);
        when(filmRepository.findById("missing")).thenReturn(Optional.empty());

        filmRatingServiceKafka.listenRatingEvent(eventPayload("e-1", "missing", 5, 0), ack);

        verify(filmRepository, never()).save(any());
        verifyNoInteractions(filmService);
        verify(ack).acknowledge();
    }

    @Test
    void listenRatingEvent_alreadyProcessed_idempotentSkipButStillAcks() {
        when(redisService.getAsString("film:event:processed:rating:e-1")).thenReturn("1");

        filmRatingServiceKafka.listenRatingEvent(eventPayload("e-1", "film-1", 5, 0), ack);

        verifyNoInteractions(filmRepository, filmService);
        verify(ack).acknowledge();
    }

    @Test
    void listenRatingEvent_malformedPayload_throwsAndDoesNotAck() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> filmRatingServiceKafka.listenRatingEvent("not-json", ack));

        verifyNoInteractions(ack, redisService, filmRepository);
    }
}
