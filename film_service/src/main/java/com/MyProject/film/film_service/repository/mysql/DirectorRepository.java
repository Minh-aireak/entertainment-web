package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Director;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectorRepository extends JpaRepository<Director, String> {
    Page<Director> findAllByDeletedFalse(Pageable pageable);
    Optional<Director> findByIdAndDeletedFalse(String id);
}
