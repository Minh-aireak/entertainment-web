package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Episode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, String> {
    List<Episode> findByFilm_IdOrderBySeasonNumberAscEpisodeNumberAsc(String filmId);

    @Query("select max(e.episodeNumber) from Episode e where e.film.id = :filmId")
    Optional<Integer> findMaxEpisodeNumberByFilmId(@Param("filmId") String filmId);

    // Flat, cross-film episode listing for the admin dashboard. JOIN FETCH is safe to paginate here
    // since Episode -> Film is a to-one association (no row multiplication like a to-many fetch would cause).
    @Query("""
            SELECT e FROM Episode e JOIN FETCH e.film f
            WHERE (:filmId IS NULL OR f.id = :filmId)
              AND (:title IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :title, '%')))
            ORDER BY f.title ASC, e.seasonNumber ASC, e.episodeNumber ASC
            """)
    Page<Episode> searchEpisodes(@Param("filmId") String filmId, @Param("title") String title, Pageable pageable);
}
