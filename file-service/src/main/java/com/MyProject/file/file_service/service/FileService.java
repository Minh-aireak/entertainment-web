package com.MyProject.file.file_service.service;

import com.MyProject.file.file_service.dto.response.FileDownload;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.entity.FileMgmt;
import com.MyProject.file.file_service.entity.ImageFile;
import com.MyProject.file.file_service.entity.VideoFile;
import com.MyProject.file.file_service.exception.AppException;
import com.MyProject.file.file_service.enums.ErrorCode;
import com.MyProject.file.file_service.repository.FileMgmtRepository;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileService {
    S3Client s3Client;
    S3Presigner s3Presigner;
    FileMgmtRepository fileMgmtRepository;

    // Trạng thái các phiên chunked-upload đang chạy dở. Chỉ tồn tại trong bộ nhớ của instance này,
    // giống hạn chế cũ khi dùng thư mục tạm trên đĩa local: nếu chạy nhiều instance sau load balancer,
    // các request của cùng 1 uploadId phải luôn về cùng 1 instance.
    Map<String, MultipartSession> chunkedUploadSessions = new ConcurrentHashMap<>();

    @NonFinal
    @Value("${b2.bucket-name}")
    String bucketName;

    @NonFinal
    @Value("${b2.endpoint}")
    String endpoint;

    @NonFinal
    @Value("${b2.public:false}")
    boolean bucketPublic;

    @NonFinal
    @Value("${b2.presigned-url-ttl:PT1H}")
    Duration presignedUrlTtl;

    // b2.endpoint is already fail-fast-checked in B2Config; bucket-name isn't validated by anyone,
    // so a blank value used to sail through startup and only blow up as a confusing SDK/HTTP error
    // on the first upload.
    @PostConstruct
    private void validateConfig() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException(
                    "B2 is not configured: 'b2.bucket-name' is blank. Set the B2_BUCKET_NAME " +
                            "environment variable before starting file-service.");
        }
    }

    @NonFinal
    @Value("${file.max-image-size:25MB}")
    DataSize maxImageSize;

    @NonFinal
    @Value("${file.max-video-size:5GB}")
    DataSize maxVideoSize;

    @NonFinal
    @Value("${file.max-raw-size:100MB}")
    DataSize maxRawSize;

    // Ngưỡng để quyết định PutObject 1 lần hay Multipart Upload cho luồng upload không-chunk.
    static final long MULTIPART_THRESHOLD = 20L * 1024 * 1024;
    // S3-compatible API (bao gồm B2) yêu cầu mỗi part >= 5MB, trừ part cuối cùng.
    static final int SELF_DRIVEN_PART_SIZE = 10 * 1024 * 1024;
    // CopyObject 1 lần của S3 API chỉ nhận object nguồn tối đa 5GiB, để margin an toàn dưới ranh giới đó.
    static final long COPY_OBJECT_MAX_SIZE = 4_500_000_000L;
    // Kích thước mỗi part khi phải multipart-copy (UploadPartCopy) cho object vượt COPY_OBJECT_MAX_SIZE.
    static final long COPY_PART_SIZE = 1024L * 1024 * 1024;

    private record MultipartSession(String key, String s3UploadId, ConcurrentMap<Integer, CompletedPart> parts) {
    }

    @Transactional
    public FileResponse uploadFile(MultipartFile multipartFile) {
        validateFile(multipartFile);

        String contentType = multipartFile.getContentType();
        String key = buildObjectKey(multipartFile.getOriginalFilename());

        try {
            byte[] bytesForProbing = null;

            if (multipartFile.getSize() > MULTIPART_THRESHOLD) {
                uploadInSelfDrivenMultipart(multipartFile, key, contentType);
            } else {
                bytesForProbing = multipartFile.getBytes();
                putObject(key, contentType, bytesForProbing);
            }

            String ownerId = SecurityUtils.getCurrentUserId();
            FileMgmt fileMgmt = buildFileMgmt(key, contentType, multipartFile.getSize(),
                    multipartFile.getOriginalFilename(), ownerId, bytesForProbing);
            fileMgmtRepository.save(fileMgmt);

            return mapToFileResponse(fileMgmt);

        } catch (IOException e) {
            log.error("Error reading uploaded file", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        } catch (SdkException e) {
            log.error("Error uploading file to Backblaze B2", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    private void uploadInSelfDrivenMultipart(MultipartFile file, String key, String contentType) throws IOException {
        String s3UploadId = createMultipartUpload(key, contentType);
        List<CompletedPart> completedParts = new ArrayList<>();

        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[SELF_DRIVEN_PART_SIZE];
            int partNumber = 1;
            int read;
            while ((read = readFully(in, buffer)) > 0) {
                byte[] partData = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                completedParts.add(uploadPart(key, s3UploadId, partNumber, partData));
                partNumber++;
            }
            completeMultipartUpload(key, s3UploadId, completedParts);
        } catch (IOException | SdkException e) {
            abortMultipartUploadQuietly(key, s3UploadId);
            throw e;
        }
    }

    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        int read;
        while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
        }
        return total;
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private void putObject(String key, String contentType, byte[] data) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private String createMultipartUpload(String key, String contentType) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        return s3Client.createMultipartUpload(request).uploadId();
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private CompletedPart uploadPart(String key, String s3UploadId, int partNumber, byte[] data) {
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(s3UploadId)
                .partNumber(partNumber)
                .build();
        String eTag = s3Client.uploadPart(request, RequestBody.fromBytes(data)).eTag();
        return CompletedPart.builder().partNumber(partNumber).eTag(eTag).build();
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private void completeMultipartUpload(String key, String s3UploadId, List<CompletedPart> parts) {
        List<CompletedPart> sorted = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .toList();
        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(s3UploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(sorted).build())
                .build());
    }

    private void abortMultipartUploadQuietly(String key, String s3UploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .uploadId(s3UploadId)
                    .build());
        } catch (SdkException e) {
            log.warn("Failed to abort multipart upload for key {}", key, e);
        }
    }

    private void validateFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        long size = file.getSize();
        long maxSize;
        if (contentType.startsWith("image/")) {
            maxSize = maxImageSize.toBytes();
        } else if (contentType.startsWith("video/")) {
            maxSize = maxVideoSize.toBytes();
        } else {
            maxSize = maxRawSize.toBytes();
        }

        if (size > maxSize) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private FileMgmt buildFileMgmt(String key, String contentType, long fileSize, String originalName,
                                    String ownerId, byte[] bytesForProbing) {
        boolean isImage = contentType != null && contentType.startsWith("image/");

        if (isImage) {
            Integer width = null;
            Integer height = null;
            String format = extractExtension(originalName);

            if (bytesForProbing != null) {
                try {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytesForProbing));
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                } catch (IOException e) {
                    log.warn("Could not read image dimensions for {}", originalName, e);
                }
            }

            return ImageFile.builder()
                    .id(key)
                    .ownerId(ownerId)
                    .contentType(contentType)
                    .size(fileSize)
                    .path(key)
                    .originalName(originalName)
                    .width(width)
                    .height(height)
                    .format(format)
                    .build();
        } else {
            // Khác với Cloudinary, B2/S3 không tự trích metadata video (duration, resolution, bitrate).
            // Các trường này sẽ để trống trừ khi tích hợp thêm công cụ đọc media (vd. ffprobe).
            return VideoFile.builder()
                    .id(key)
                    .ownerId(ownerId)
                    .contentType(contentType)
                    .size(fileSize)
                    .path(key)
                    .originalName(originalName)
                    .build();
        }
    }

    private FileResponse mapToFileResponse(FileMgmt fileMgmt) {
        FileResponse.FileResponseBuilder builder = FileResponse.builder()
                .id(fileMgmt.getId())
                .url(resolvePublicUrl(fileMgmt.getPath(), fileMgmt.getContentType()))
                .type(fileMgmt.getContentType())
                .size(fileMgmt.getSize());

        if (fileMgmt instanceof ImageFile imageFile) {
            builder.width(imageFile.getWidth())
                    .height(imageFile.getHeight())
                    .format(imageFile.getFormat());
        } else if (fileMgmt instanceof VideoFile videoFile) {
            builder.duration(videoFile.getDuration())
                    .resolution(videoFile.getResolution());
        }

        return builder.build();
    }

    private String resolvePublicUrl(String key, String contentType) {
        if (bucketPublic) {
            return "https://" + endpoint + "/" + bucketName + "/" + key;
        }
        // Bucket Private: sinh presigned URL có hạn dùng, luôn tạo mới tại thời điểm trả response
        // thay vì lưu cố định trong DB để tránh URL hết hạn. Content-Type đúng được ép qua
        // responseContentType ngay trên presigned URL, nên object gốc không cần sửa metadata.
        GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key);
        if (contentType != null && !contentType.isBlank()) {
            getObjectRequest.responseContentType(contentType);
        }

        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(presignedUrlTtl)
                        .getObjectRequest(getObjectRequest.build())
                        .build())
                .url()
                .toString();
    }

    private String buildObjectKey(String originalFilename) {
        String extension = extractExtension(originalFilename);
        String base = "movie-platform/" + UUID.randomUUID();
        return extension.isBlank() ? base : base + "." + extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < originalFilename.length() - 1)
                ? originalFilename.substring(dotIndex + 1)
                : "";
    }

    @Transactional(readOnly = true)
    public FileDownload downloadFile(String fileId) {
        fileMgmtRepository.findById(fileId).orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
        throw new UnsupportedOperationException("Direct file download is not supported. Use the provided URL from file info.");
    }

    public String initChunkedUpload() {
        try {
            // Chưa biết tên/content-type thật của file ở bước này (client chỉ gửi ở completeChunkedUpload),
            // nên khởi tạo multipart upload với content-type tạm rồi sửa lại khi hoàn tất.
            String key = buildObjectKey(null);
            String s3UploadId = createMultipartUpload(key, "application/octet-stream");

            String localUploadId = UUID.randomUUID().toString();
            chunkedUploadSessions.put(localUploadId, new MultipartSession(key, s3UploadId, new ConcurrentHashMap<>()));
            return localUploadId;
        } catch (SdkException e) {
            log.error("Error initializing chunked upload on B2", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        MultipartSession session = chunkedUploadSessions.get(uploadId);
        if (session == null) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }

        try {
            int partNumber = chunkIndex + 1; // S3 part number bắt đầu từ 1, chunkIndex của client bắt đầu từ 0
            CompletedPart part = uploadPart(session.key(), session.s3UploadId(), partNumber, file.getBytes());
            session.parts().put(partNumber, part);
        } catch (IOException e) {
            log.error("Error reading chunk {}", chunkIndex, e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        } catch (SdkException e) {
            log.error("Error uploading chunk {} to B2", chunkIndex, e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    @Transactional
    public FileResponse completeChunkedUpload(String uploadId, String fileName, String contentType) {
        MultipartSession session = chunkedUploadSessions.remove(uploadId);
        if (session == null) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }

        try {
            completeMultipartUpload(session.key(), session.s3UploadId(), List.copyOf(session.parts().values()));
            long size = headObjectSize(session.key());

            String finalKey = session.key();
            if (bucketPublic) {
                // Bucket Public: GET ẩn danh không cho override response header, nên phải sửa
                // content-type thật trên object (đổi luôn key cho có phần mở rộng cho gọn).
                finalKey = finalizeObjectKeyAndContentType(session.key(), fileName, contentType, size);
            }
            // Bucket Private: giữ nguyên key tạm - content-type đúng được ép lúc ký presigned URL
            // (xem resolvePublicUrl), không cần đụng tới object.

            String ownerId = SecurityUtils.getCurrentUserId();
            FileMgmt fileMgmt = buildFileMgmt(finalKey, contentType, size, fileName, ownerId, null);
            fileMgmtRepository.save(fileMgmt);

            return mapToFileResponse(fileMgmt);

        } catch (SdkException e) {
            log.error("Error completing chunked upload {}", uploadId, e);
            abortMultipartUploadQuietly(session.key(), session.s3UploadId());
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    // Đổi tên object từ key tạm sang key có đúng phần mở rộng, đồng thời sửa lại content-type
    // (không thể set content-type cho object multipart sau khi đã complete, ngoại trừ qua Copy).
    // CopyObject 1 lần chỉ nhận object <= ~5GiB, object lớn hơn phải multipart-copy (UploadPartCopy).
    private String finalizeObjectKeyAndContentType(String tempKey, String fileName, String contentType, long totalSize) {
        String finalKey = buildObjectKey(fileName);
        if (totalSize <= COPY_OBJECT_MAX_SIZE) {
            copyObject(tempKey, finalKey, contentType);
        } else {
            multipartCopy(tempKey, finalKey, contentType, totalSize);
        }
        deleteObject(tempKey);
        return finalKey;
    }

    private void multipartCopy(String sourceKey, String destKey, String contentType, long totalSize) {
        String uploadId = createMultipartUpload(destKey, contentType);
        List<CompletedPart> parts = new ArrayList<>();

        try {
            int partNumber = 1;
            for (long offset = 0; offset < totalSize; offset += COPY_PART_SIZE) {
                long rangeEnd = Math.min(offset + COPY_PART_SIZE, totalSize) - 1;
                parts.add(uploadPartCopy(sourceKey, destKey, uploadId, partNumber, offset, rangeEnd));
                partNumber++;
            }
            completeMultipartUpload(destKey, uploadId, parts);
        } catch (SdkException e) {
            abortMultipartUploadQuietly(destKey, uploadId);
            throw e;
        }
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private CompletedPart uploadPartCopy(String sourceKey, String destKey, String uploadId, int partNumber,
                                          long rangeStart, long rangeEnd) {
        UploadPartCopyResponse response = s3Client.uploadPartCopy(UploadPartCopyRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(bucketName)
                .destinationKey(destKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .copySourceRange("bytes=" + rangeStart + "-" + rangeEnd)
                .build());
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.copyPartResult().eTag())
                .build();
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private void copyObject(String sourceKey, String destKey, String contentType) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(bucketName)
                .destinationKey(destKey)
                .contentType(contentType)
                .metadataDirective(MetadataDirective.REPLACE)
                .build());
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    @CircuitBreaker(name = "b2")
    @RateLimiter(name = "b2")
    private long headObjectSize(String key) {
        return s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build()).contentLength();
    }

    public FileResponse getFileInfo(String fileId) {
        FileMgmt fileMgmt = fileMgmtRepository.findById(fileId).orElseThrow(() ->
                new AppException(ErrorCode.FILE_NOT_FOUND));
        return mapToFileResponse(fileMgmt);
    }
}
