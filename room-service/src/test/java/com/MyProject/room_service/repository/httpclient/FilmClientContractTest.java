package com.MyProject.room_service.repository.httpclient;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;

class FilmClientContractTest {

    @Test
    void getEpisodesByFilm_includesFilmServiceContextPath() throws NoSuchMethodException {
        GetMapping mapping = FilmClient.class
                .getMethod("getEpisodesByFilm", String.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/films/episodes/film/{filmId}");
    }
}
