package com.MyProject.film.film_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "directors")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Director {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false)
    String name;

    // URL ngoài do admin tự nhập (vd. placehold.co) - dùng thẳng, không hết hạn.
    @Column(columnDefinition = "TEXT")
    String avatarUrl;

    // Nếu khác null: ảnh được upload qua file-service (bucket B2 private) - avatarUrl ở trên bị bỏ
    // qua, URL hiển thị được resolve mới mỗi lần đọc (xem DirectorService) để tránh presigned URL hết hạn.
    @Column(columnDefinition = "TEXT")
    String avatarFileId;

    boolean deleted;
}
