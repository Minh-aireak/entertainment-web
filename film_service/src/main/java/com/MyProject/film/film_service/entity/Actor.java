package com.MyProject.film.film_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "actors")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Actor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false)
    String name;

    // URL ngoài do admin tự nhập (vd. placehold.co) - dùng thẳng, không hết hạn.
    @Column(columnDefinition = "TEXT")
    String avatarUrl;

    // Nếu khác null: ảnh được upload qua file-service (bucket B2 private) - avatarUrl ở trên bị bỏ
    // qua, URL hiển thị được resolve mới mỗi lần đọc (xem ActorService) để tránh presigned URL hết hạn.
    @Column(columnDefinition = "TEXT")
    String avatarFileId;

    boolean deleted;
}
