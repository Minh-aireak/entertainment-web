package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.FilmDirector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmDirectorRepository extends JpaRepository<FilmDirector, String> {
    void deleteByFilm_Id(String filmId);
}
