package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.document.FilmDoc;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilmServiceKafkaTest {

    @Mock FilmElasticRepository filmElasticRepository;
    @Mock RedisService redisService;
    @Mock Acknowledgment ack;

    FilmServiceKafka filmServiceKafka;

    @BeforeEach
    void setUp() {
        filmServiceKafka = new FilmServiceKafka(filmElasticRepository, new ObjectMapper(), redisService);
    }

    @Test
    void listenFilmSync_happyPath_savesToElasticAndMarksProcessedAndAcks() {
        when(redisService.getAsString("film:event:processed:sync:film-1")).thenReturn(null);
        String payload = "{\"id\":\"film-1\",\"title\":\"Title\"}";

        filmServiceKafka.listenFilmSync(payload, ack);

        verify(filmElasticRepository).save(argThat((FilmDoc doc) -> doc.getId().equals("film-1")));
        verify(redisService).setWithExpiration(eq("film:event:processed:sync:film-1"), eq("1"), eq(7L), any());
        verify(ack).acknowledge();
    }

    @Test
    void listenFilmSync_alreadyProcessed_skipsSaveButStillAcks() {
        when(redisService.getAsString("film:event:processed:sync:film-1")).thenReturn("1");
        String payload = "{\"id\":\"film-1\",\"title\":\"Title\"}";

        filmServiceKafka.listenFilmSync(payload, ack);

        verifyNoInteractions(filmElasticRepository);
        verify(ack).acknowledge();
    }

    @Test
    void listenFilmSync_malformedPayload_throwsAndDoesNotAck() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> filmServiceKafka.listenFilmSync("not-json", ack));

        verifyNoInteractions(ack, filmElasticRepository, redisService);
    }

    @Test
    void listenFilmSync_elasticSaveFails_throwsAndDoesNotAck() {
        when(redisService.getAsString("film:event:processed:sync:film-1")).thenReturn(null);
        when(filmElasticRepository.save(any())).thenThrow(new RuntimeException("elasticsearch down"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> filmServiceKafka.listenFilmSync("{\"id\":\"film-1\"}", ack));

        verify(ack, never()).acknowledge();
    }
}
