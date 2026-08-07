package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmRepository extends JpaRepository<Film, String> {
    Page<Film> findByStatus(FilmStatus status, Pageable pageable);

    @Query("""
            SELECT f FROM Film f
            WHERE f.series = :series
              AND :excludedGenre NOT MEMBER OF f.genres
              AND (:country IS NULL OR f.country = :country)
              AND (:genre IS NULL OR :genre MEMBER OF f.genres)
            """)
    Page<Film> findSeriesOrStandalone(@Param("series") boolean series,
                                       @Param("excludedGenre") Genre excludedGenre,
                                       @Param("country") Country country,
                                       @Param("genre") Genre genre,
                                       Pageable pageable);

    @Query("""
            SELECT f FROM Film f
            WHERE :genre MEMBER OF f.genres
              AND (:country IS NULL OR f.country = :country)
            """)
    Page<Film> findByGenreContaining(@Param("genre") Genre genre,
                                      @Param("country") Country country,
                                      Pageable pageable);
}
