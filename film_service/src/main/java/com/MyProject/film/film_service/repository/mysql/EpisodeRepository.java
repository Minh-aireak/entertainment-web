package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, String> {
    List<Episode> findByFilm_IdOrderBySeasonNumberAscEpisodeNumberAsc(String filmId);
}
