package com.MyProject.file.file_service.entity;

import com.MyProject.file.file_service.enums.HlsStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "file_mgmt")
@TypeAlias("VIDEO")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VideoFile extends FileMgmt {
    Long duration;          // Giây
    String resolution;      // "1920x1080"
    String thumbnailUrl;    // Ảnh thumbnail của video
    Long bitrate;

    // null = doc cũ trước khi có HLS, hoặc upload không bật enableHls -> luôn fallback sang URL MP4
    // presigned (xem FileService.mapToFileResponse). Chỉ khi READY mới trả URL manifest HLS.
    HlsStatus hlsStatus;
    // Dùng để HlsTranscodeJob nhận lại job PROCESSING bị bỏ dở do crash/restart (query claim chỉ
    // khớp PENDING nên nếu không có cơ chế này, job kẹt ở PROCESSING vĩnh viễn).
    Instant hlsProcessingStartedAt;
    // Lý do fail ngắn gọn (vd exit code + tail log ffmpeg), cắt bớt trước khi lưu - chỉ để debug,
    // không hiển thị cho client.
    String hlsFailureReason;
}