package com.MyProject.file.file_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitPresignedUploadRequest {
    String fileName;
    String contentType;
    Long fileSize;
    // Opt-in: chỉ EpisodeUpload (film_service) bật cờ này. Các luồng upload video khác (vd clip
    // chat) không cần/không nên bị transcode HLS - xem FileService.buildFileMgmt.
    boolean enableHls;
    // Thời lượng video do trình duyệt đọc trực tiếp từ metadata file, tính bằng giây.
    Long durationSeconds;
}
