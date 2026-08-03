package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.FilmFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilmFollowRepository extends JpaRepository<FilmFollow, String> {
    Optional<FilmFollow> findByUserIdAndFilmId(String userId, String filmId);
    boolean existsByUserIdAndFilmId(String userId, String filmId);
    List<FilmFollow> findAllByUserId(String userId);
    List<FilmFollow> findAllByFilmId(String filmId);
    void deleteByUserIdAndFilmId(String userId, String filmId);
}
