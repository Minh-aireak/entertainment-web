package com.MyProject.film.film_service.entity;

import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "films")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class Film {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String title;

    @Column(columnDefinition = "TEXT")
    String description;

    String thumbnailUrl;

    String trailerUrl;

    int durationMinutes;

    Instant releaseDate;

    @LastModifiedDate
    Instant lastUpdate;

    Boolean series;

    double averageRating = 0.0;

    int ratingCount = 0;

    int followCount = 0;

    int episodeCount = 0;

    int season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_id")
    Director director;

    @Enumerated(EnumType.STRING)
    Country country;

    @Enumerated(EnumType.STRING)
    FilmStatus status = FilmStatus.UPCOMING;

    @ElementCollection(targetClass = Genre.class)
    @CollectionTable(name = "film_genres", joinColumns = @JoinColumn(name = "film_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "genre")
    Set<Genre> genres = new HashSet<>();

    @OneToMany(mappedBy = "film")
    List<Episode> episodes = new ArrayList<>();

    @OneToMany(mappedBy = "film")
    List<FilmCast> casts = new ArrayList<>();
}
