package com.MyProject.file.file_service.service;

import com.MyProject.file.file_service.entity.VideoFile;
import com.MyProject.file.file_service.enums.HlsStatus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

// Worker nền cho HLS: đóng gói lại (remux, KHÔNG encode lại - "-c copy") video MP4 gốc thành segment
// .ts + manifest .m3u8, đúng kiểu job "@Scheduled + poll DB" đã dùng ở RatingLikeNotificationFlushJob
// (film_service) - không cần thêm Kafka/outbox vì đây thuần là việc nội bộ 1 service, không có
// service khác cần biết. @Scheduled mặc định chạy tuần tự trên 1 luồng nên job luôn xử lý từng video
// một - đây chính là cơ chế giới hạn CPU thực sự (không phải cờ -threads), phù hợp máy yếu.
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HlsTranscodeJob {
    MongoTemplate mongoTemplate;
    S3Client s3Client;

    @NonFinal
    @Value("${b2.bucket-name}")
    String bucketName;

    @NonFinal
    @Value("${hls.segment-duration-seconds:6}")
    int segmentDurationSeconds;

    @NonFinal
    @Value("${hls.ffmpeg-timeout-minutes:120}")
    long ffmpegTimeoutMinutes;

    @NonFinal
    @Value("${hls.stale-processing-minutes:30}")
    long staleProcessingMinutes;

    @Scheduled(fixedDelayString = "${hls.poll-interval-ms:10000}")
    public void processNextPendingVideo() {
        reclaimStaleProcessingJobs();

        VideoFile claimed = claimNextPendingVideo();
        if (claimed == null) {
            return;
        }

        try {
            transcodeAndUpload(claimed);
            log.info("HLS transcode succeeded for key={}", claimed.getId());
        } catch (Exception e) {
            log.error("HLS transcode failed for key={}", claimed.getId(), e);
            markFailed(claimed.getId(), e.getMessage());
        }
    }

    // Claim nguyên tử PENDING -> PROCESSING. findAndModify(..., VideoFile.class) tự giới hạn theo
    // discriminator "_class"/"type" của Spring Data (xem FileMgmt @JsonTypeInfo/@TypeAlias), nên
    // ImageFile trong cùng collection "file_mgmt" không bao giờ khớp criteria "hlsStatus is PENDING"
    // (field đó không tồn tại trên ImageFile). An toàn ngay cả khi chỉ chạy 1 instance như hiện tại;
    // miễn phí đúng đắn nếu sau này chạy nhiều instance.
    private VideoFile claimNextPendingVideo() {
        Query query = Query.query(Criteria.where("hlsStatus").is(HlsStatus.PENDING));
        Update update = new Update()
                .set("hlsStatus", HlsStatus.PROCESSING)
                .set("hlsProcessingStartedAt", Instant.now());
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), VideoFile.class);
    }

    // Đưa các job kẹt ở PROCESSING (do crash/restart giữa chừng) về lại PENDING sau
    // staleProcessingMinutes - nếu không, claimNextPendingVideo() (chỉ khớp PENDING) sẽ không bao giờ
    // nhặt lại được video đó nữa.
    private void reclaimStaleProcessingJobs() {
        Instant staleBefore = Instant.now().minus(staleProcessingMinutes, ChronoUnit.MINUTES);
        Query query = Query.query(Criteria.where("hlsStatus").is(HlsStatus.PROCESSING)
                .and("hlsProcessingStartedAt").lt(staleBefore));
        Update update = new Update().set("hlsStatus", HlsStatus.PENDING).unset("hlsProcessingStartedAt");
        mongoTemplate.updateMulti(query, update, VideoFile.class);
    }

    private void transcodeAndUpload(VideoFile video) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("hls-job-");
        try {
            // s3Client.getObject(request, Path) đòi hỏi file đích CHƯA tồn tại - workDir vừa tạo
            // trống nên inputFile chắc chắn chưa có, khác với Files.createTempFile (tạo sẵn file rỗng).
            Path inputFile = workDir.resolve("source" + extensionOf(video.getId()));
            Path outputDir = Files.createDirectory(workDir.resolve("out"));

            s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(video.getId()).build(), inputFile);

            runFfmpeg(inputFile, outputDir);

            // Upload toàn bộ output TRƯỚC khi set READY - READY là tín hiệu duy nhất client quan sát
            // được (qua getFileInfo), nên chỉ được bật sau khi mọi object đã tồn tại trên B2, tránh
            // client nhận manifest trỏ tới segment chưa kịp upload.
            uploadHlsOutputs(video.getId(), outputDir);
            markReady(video.getId());
        } finally {
            FileSystemUtils.deleteRecursively(workDir);
        }
    }

    private void runFfmpeg(Path input, Path outputDir) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffmpeg", "-y", "-nostdin", "-loglevel", "warning",
                "-i", input.toString(),
                // Bỏ sub/track phụ mà muxer mpegts không remux được, chỉ giữ 1 video + tối đa 1 audio.
                "-map", "0:v:0", "-map", "0:a:0?",
                "-c", "copy",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDurationSeconds),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", outputDir.resolve("segment%05d.ts").toString(),
                outputDir.resolve("playlist.m3u8").toString());

        Path logFile = outputDir.getParent().resolve("ffmpeg.log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                .start();

        boolean finished = process.waitFor(ffmpegTimeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg timed out after " + ffmpegTimeoutMinutes + "m for " + input);
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg exited " + process.exitValue() + ": " + tailOf(logFile, 20));
        }
    }

    private void uploadHlsOutputs(String originalKey, Path outputDir) throws IOException {
        String prefix = HlsKeys.prefixFor(originalKey);
        try (Stream<Path> files = Files.list(outputDir)) {
            for (Path file : files.toList()) {
                String key = prefix + file.getFileName();
                String contentType = file.toString().endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl" : "video/mp2t";
                s3Client.putObject(
                        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
                        RequestBody.fromFile(file));
            }
        }
    }

    private void markReady(String key) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(key)),
                new Update().set("hlsStatus", HlsStatus.READY).unset("hlsProcessingStartedAt").unset("hlsFailureReason"),
                VideoFile.class);
    }

    private void markFailed(String key, String reason) {
        String trimmed = reason == null ? "unknown error" : reason.substring(0, Math.min(reason.length(), 500));
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(key)),
                new Update().set("hlsStatus", HlsStatus.FAILED).set("hlsFailureReason", trimmed).unset("hlsProcessingStartedAt"),
                VideoFile.class);
    }

    private static String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot >= 0 ? key.substring(dot) : "";
    }

    private static String tailOf(Path logFile, int maxLines) throws IOException {
        List<String> lines = Files.readAllLines(logFile);
        int from = Math.max(0, lines.size() - maxLines);
        return String.join("\n", lines.subList(from, lines.size()));
    }
}
