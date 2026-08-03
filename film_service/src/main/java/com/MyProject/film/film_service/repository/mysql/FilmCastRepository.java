package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.FilmCast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmCastRepository extends JpaRepository<FilmCast, String> {
}
