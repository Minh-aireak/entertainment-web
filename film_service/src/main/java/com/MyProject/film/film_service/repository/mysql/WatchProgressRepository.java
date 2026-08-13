package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.WatchProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchProgressRepository extends JpaRepository<WatchProgress, String> {
    Optional<WatchProgress> findByUserIdAndFilmId(String userId, String filmId);

    // Excludes rows the user has effectively finished (>=95% watched) so "Continue Watching"
    // doesn't keep showing films they already finished.
    @Query("""
            SELECT w FROM WatchProgress w
            WHERE w.userId = :userId
              AND (w.durationSeconds <= 0 OR w.positionSeconds < w.durationSeconds * 0.95)
            ORDER BY w.updatedAt DESC
            """)
    List<WatchProgress> findContinueWatching(@Param("userId") String userId, Pageable pageable);

    void deleteByUserIdAndFilmId(String userId, String filmId);
}
