package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Film;
import com.MyProject.film.film_service.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, String> {
    Optional<Rating> findByFilmAndUserId(Film film, String userId);
    Optional<Rating> findByFilmIdAndUserId(String filmId, String userId);
}
