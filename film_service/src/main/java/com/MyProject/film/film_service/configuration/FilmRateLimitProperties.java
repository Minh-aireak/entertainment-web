package com.MyProject.film.film_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class FilmRateLimitProperties {
    Rule filmRating = new Rule();
    Rule filmFollow = new Rule();
    Rule readFilms = new Rule();
    Rule searchFilms = new Rule();
    Rule readFilmDetail = new Rule();
    Rule createFilm = new Rule();
    Rule readFollowedFilms = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
