package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.enums.FilmStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FilmRepository extends JpaRepository<Film, String> {
    Page<Film> findByStatus(FilmStatus status, Pageable pageable);
}
