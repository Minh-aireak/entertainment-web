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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilmFollowServiceKafkaTest {

    @Mock FilmRepository filmRepository;
    @Mock FilmService filmService;
    @Mock RedisService redisService;
    @Mock Acknowledgment ack;

    FilmFollowServiceKafka filmFollowServiceKafka;

    @BeforeEach
    void setUp() {
        filmFollowServiceKafka = new FilmFollowServiceKafka(filmRepository, new ObjectMapper(), filmService, redisService);
    }

    private String eventPayload(String eventId, String filmId, String action) {
        return "{\"eventId\":\"" + eventId + "\",\"filmId\":\"" + filmId + "\",\"userId\":\"user-1\",\"action\":\"" + action + "\"}";
    }

    @Test
    void listenFollowEvent_follow_incrementsFollowCountAndSyncsElasticAndAcks() {
        Film film = new Film();
        film.setId("film-1");
        film.setFollowCount(2);
        when(redisService.getAsString("film:event:processed:follow:e-1")).thenReturn(null);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));

        filmFollowServiceKafka.listenFollowEvent(eventPayload("e-1", "film-1", "FOLLOW"), ack);

        assertThat(film.getFollowCount()).isEqualTo(3);
        verify(filmRepository).save(film);
        verify(filmService).syncFilmToElasticsearch(film);
        verify(redisService).setWithExpiration(eq("film:event:processed:follow:e-1"), eq("1"), eq(7L), any());
        verify(ack).acknowledge();
    }

    @Test
    void listenFollowEvent_unfollow_decrementsFollowCountButNeverGoesNegative() {
        Film film = new Film();
        film.setId("film-1");
        film.setFollowCount(0);
        when(redisService.getAsString(anyString())).thenReturn(null);
        when(filmRepository.findById("film-1")).thenReturn(Optional.of(film));

        filmFollowServiceKafka.listenFollowEvent(eventPayload("e-1", "film-1", "UNFOLLOW"), ack);

        assertThat(film.getFollowCount()).isEqualTo(0);
        verify(ack).acknowledge();
    }

    @Test
    void listenFollowEvent_filmNotFound_skipsUpdateButStillMarksProcessedAndAcks() {
        when(redisService.getAsString(anyString())).thenReturn(null);
        when(filmRepository.findById("missing")).thenReturn(Optional.empty());

        filmFollowServiceKafka.listenFollowEvent(eventPayload("e-1", "missing", "FOLLOW"), ack);

        verify(filmRepository, never()).save(any());
        verifyNoInteractions(filmService);
        verify(ack).acknowledge();
    }

    @Test
    void listenFollowEvent_alreadyProcessed_idempotentSkipButStillAcks() {
        when(redisService.getAsString("film:event:processed:follow:e-1")).thenReturn("1");

        filmFollowServiceKafka.listenFollowEvent(eventPayload("e-1", "film-1", "FOLLOW"), ack);

        verifyNoInteractions(filmRepository, filmService);
        verify(ack).acknowledge();
    }

    @Test
    void listenFollowEvent_malformedPayload_throwsAndDoesNotAck() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> filmFollowServiceKafka.listenFollowEvent("not-json", ack));

        verifyNoInteractions(ack, redisService, filmRepository);
    }
}
