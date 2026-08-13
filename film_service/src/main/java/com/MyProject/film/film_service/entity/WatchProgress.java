package com.MyProject.film.film_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(
        name = "watch_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id", "film_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class WatchProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "film_id")
    Film film;

    // Null for films without episodes; set to whichever episode the user last watched otherwise.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    Episode episode;

    int positionSeconds;

    int durationSeconds;

    @LastModifiedDate
    Instant updatedAt;
}
