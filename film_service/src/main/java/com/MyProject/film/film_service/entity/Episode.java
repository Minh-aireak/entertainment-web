package com.MyProject.film.film_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "episodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Episode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    int seasonNumber;

    int episodeNumber;

    String title;

    // ID (object key) tra vào file-service, KHÔNG lưu URL trực tiếp: bucket B2 private nên URL trả về
    // là presigned GET có hạn dùng (mặc định 1h, xem file-service FileService.resolvePublicUrl) - lưu
    // thẳng URL sẽ khiến video ngừng phát được khi chữ ký hết hạn. Frontend gọi lại file-service bằng
    // ID này để lấy URL mới mỗi khi phát.
    @Column(columnDefinition = "TEXT")
    String videoFileId;

    int durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "film_id")
    Film film;
}
