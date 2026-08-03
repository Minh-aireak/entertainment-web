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
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileService {
    Cloudinary cloudinary;
    FileMgmtRepository fileMgmtRepository;

    static long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    static long MAX_VIDEO_SIZE = 500 * 1024 * 1024; // 500MB
    static String TEMP_DIR = "temp-uploads";

    @Transactional
    public FileResponse uploadFile(MultipartFile multipartFile) {
        validateFile(multipartFile);

        try {
            String contentType = multipartFile.getContentType();
            boolean isVideo = contentType != null && contentType.startsWith("video/");
            String resourceType = isVideo ? "video" : "image";

            Map<String, Object> params = ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "folder", "movie-platform"
            );

            Map<String, Object> uploadResult;

            if (isVideo && multipartFile.getSize() > 20 * 1024 * 1024) {
                uploadResult = uploadLargeVideo(multipartFile, params);
            } else {
                uploadResult = uploadToCloudinary(multipartFile.getBytes(), params);
            }

            String ownerId = SecurityUtils.getCurrentUserId();
            FileMgmt fileMgmt = buildFileMgmt(uploadResult, multipartFile.getContentType(),
                    multipartFile.getSize(), multipartFile.getOriginalFilename(), ownerId);
            fileMgmtRepository.save(fileMgmt);

            return mapToFileResponse(fileMgmt);

        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    @CircuitBreaker(name = "cloudinary")
    @RateLimiter(name = "cloudinary")
    private Map<String, Object> uploadToCloudinary(byte[] fileBytes, Map<String, Object> params) throws IOException {
        return cloudinary.uploader().upload(fileBytes, params);
    }

    @CircuitBreaker(name = "cloudinary")
    @RateLimiter(name = "cloudinary")
    private Map<String, Object> uploadLargeFileToCloudinary(File file, Map<String, Object> params) throws IOException {
        return cloudinary.uploader().uploadLarge(file, params);
    }

    private Map<String, Object> uploadLargeVideo(MultipartFile multipartFile, Map<String, Object> params) throws IOException {
        File tempFile = null;
        try {
            // Ghi ra file tạm
            tempFile = File.createTempFile("upload_", "_" + multipartFile.getOriginalFilename());
            multipartFile.transferTo(tempFile);

            // Thêm chunk size 50MB
            Map<String, Object> chunkedParams = new HashMap<>(params);
            chunkedParams.put("chunk_size", 50 * 1024 * 1024); // 50MB mỗi chunk

            log.info("Uploading large video: {} ({} MB)",
                    multipartFile.getOriginalFilename(),
                    multipartFile.getSize() / (1024 * 1024));

            Map<String, Object> result = uploadLargeFileToCloudinary(tempFile, chunkedParams);

            log.info("Upload completed: {}", result.get("public_id"));
            return result;

        } finally {
            // Luôn xóa file tạm
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    private void validateFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !isVideoType(contentType))) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        long size = file.getSize();
        if (contentType.startsWith("image/") && size > MAX_IMAGE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        if (contentType.startsWith("video/") && size > MAX_VIDEO_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private boolean isVideoType(String contentType) {
        return contentType.equals("video/mp4") ||
                contentType.equals("video/webm") ||
                contentType.equals("video/quicktime");
    }

    private FileMgmt buildFileMgmt(Map<String, Object> uploadResult, String contentType,
                                    long fileSize, String originalName, String ownerId) {
        String publicId = (String) uploadResult.get("public_id");
        String url = (String) uploadResult.get("secure_url");

        if (contentType != null && contentType.startsWith("image/")) {
            return ImageFile.builder()
                    .id(publicId)
                    .ownerId(ownerId)
                    .contentType(contentType)
                    .size(fileSize)
                    .path(url)
                    .originalName(originalName)
                    .width((Integer) uploadResult.get("width"))
                    .height((Integer) uploadResult.get("height"))
                    .format((String) uploadResult.get("format"))
                    .build();
        } else {
            return VideoFile.builder()
                    .id(publicId)
                    .ownerId(ownerId)
                    .contentType(contentType)
                    .size(fileSize)
                    .path(url)
                    .originalName(originalName)
                    .duration(uploadResult.get("duration") != null ? ((Double) uploadResult.get("duration")).longValue() : null)
                    .resolution(uploadResult.get("width") + "x" + uploadResult.get("height"))
                    .bitrate(uploadResult.get("bit_rate") != null ? ((Integer) uploadResult.get("bit_rate")).longValue() : null)
                    .build();
        }
    }

    private FileResponse mapToFileResponse(FileMgmt fileMgmt) {
        FileResponse.FileResponseBuilder builder = FileResponse.builder()
                .id(fileMgmt.getId())
                .url(fileMgmt.getPath())
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

    @Transactional(readOnly = true)
    public FileDownload downloadFile(String fileId) {
        // Since we use Cloudinary, we don't serve files directly, but we can return URL or handle appropriately
        // For now, keep the same logic but maybe in future we can redirect to Cloudinary URL
        fileMgmtRepository.findById(fileId).orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
        throw new UnsupportedOperationException("Direct file download is not supported. Use the provided URL from file info.");
    }

    public String initChunkedUpload() {
        String uploadId = UUID.randomUUID().toString();
        Path path = Paths.get(TEMP_DIR, uploadId);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Could not create temp directory", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
        return uploadId;
    }

    public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        Path chunkPath = Paths.get(TEMP_DIR, uploadId, chunkIndex.toString());
        try {
            file.transferTo(chunkPath);
        } catch (IOException e) {
            log.error("Error saving chunk {}", chunkIndex, e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    @Transactional
    public FileResponse completeChunkedUpload(String uploadId, String fileName, String contentType) {
        Path uploadPath = Paths.get(TEMP_DIR, uploadId);
        File mergedFile = new File(TEMP_DIR, uploadId + "_" + fileName);

        try {
            // Merge chunks
            List<Path> chunks;
            try (var stream = Files.list(uploadPath)) {
                chunks = stream
                        .sorted(Comparator.comparing(p -> Integer.parseInt(p.getFileName().toString())))
                        .toList();
            }

            try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
                for (Path chunk : chunks) {
                    Files.copy(chunk, fos);
                }
            }

            // Upload to Cloudinary
            boolean isVideo = contentType != null && contentType.startsWith("video/");
            String resourceType = isVideo ? "video" : "image";

            Map<String, Object> params = ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "folder", "movie-platform"
            );

            Map<String, Object> uploadResult;
            if (isVideo && mergedFile.length() > 20 * 1024 * 1024) {
                uploadResult = uploadLargeFileToCloudinary(mergedFile, params);
            } else {
                uploadResult = cloudinary.uploader().upload(mergedFile, params);
            }

            String ownerId = SecurityUtils.getCurrentUserId();
            FileMgmt fileMgmt = buildFileMgmt(uploadResult, contentType,
                    mergedFile.length(), fileName, ownerId);
            fileMgmtRepository.save(fileMgmt);

            // Cleanup
            cleanup(uploadId, mergedFile);

            return mapToFileResponse(fileMgmt);

        } catch (IOException e) {
            log.error("Error completing chunked upload", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    private void cleanup(String uploadId, File mergedFile) {
        try {
            Path uploadPath = Paths.get(TEMP_DIR, uploadId);
            if (Files.exists(uploadPath)) {
                try (var stream = Files.walk(uploadPath)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
            if (mergedFile.exists()) {
                mergedFile.delete();
            }
        } catch (IOException e) {
            log.warn("Cleanup failed for uploadId: {}", uploadId, e);
        }
    }

    public FileResponse getFileInfo(String fileId) {
        FileMgmt fileMgmt = fileMgmtRepository.findById(fileId).orElseThrow(() ->
                new AppException(ErrorCode.FILE_NOT_FOUND));
        return mapToFileResponse(fileMgmt);
    }
}
