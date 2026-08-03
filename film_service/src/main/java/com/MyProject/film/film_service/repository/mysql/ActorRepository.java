package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActorRepository extends JpaRepository<Actor, String> {
    Page<Actor> findAllByDeletedFalse(Pageable pageable);
    Optional<Actor> findByIdAndDeletedFalse(String id);
}
