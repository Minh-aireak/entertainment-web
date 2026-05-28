package com.MyProject.identity.identity_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "outbox")
@EntityListeners(AuditingEntityListener.class)
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String topic;

    @Column(columnDefinition = "LONGTEXT")
    String payload;

    @CreatedDate
    LocalDateTime createdAt;

    boolean processed;
}
