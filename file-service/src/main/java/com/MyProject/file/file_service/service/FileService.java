package com.MyProject.file.file_service.service;

import com.MyProject.file.file_service.dto.request.CompletePresignedUploadRequest;
import com.MyProject.file.file_service.dto.request.CompletedPartRequest;
import com.MyProject.file.file_service.dto.request.InitPresignedUploadRequest;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.dto.response.InitPresignedUploadResponse;
import com.MyProject.file.file_service.dto.response.PresignedPartResponse;
import com.MyProject.file.file_service.entity.FileMgmt;
import com.MyProject.file.file_service.entity.ImageFile;
import com.MyProject.file.file_service.entity.VideoFile;
import com.MyProject.file.file_service.exception.AppException;
import com.MyProject.file.file_service.enums.ErrorCode;
import com.MyProject.file.file_service.enums.HlsStatus;
import com.MyProject.file.file_service.repository.FileMgmtRepository;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;
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
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileService {
    S3Client s3Client;
    S3Presigner s3Presigner;
    FileMgmtRepository fileMgmtRepository;
    CircuitBreakerRegistry circuitBreakerRegistry;
    RateLimiterRegistry rateLimiterRegistry;

    // Trạng thái các phiên presigned-upload đang chạy dở. Chỉ tồn tại trong bộ nhớ của instance này:
    // nếu chạy nhiều instance sau load balancer, các request của cùng 1 uploadId phải luôn về cùng
    // 1 instance (init/complete). Bản thân việc PUT từng part thì client gọi thẳng B2, không qua đây.
    Map<String, PresignedMultipartSession> presignedUploadSessions = new ConcurrentHashMap<>();

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

    // TTL riêng cho URL client dùng để PUT thẳng part lên B2 (uploadInitPresignedUpload) - ngắn hơn
    // presignedUrlTtl (vốn cho GET link chia sẻ) vì client được kỳ vọng bắt đầu upload gần như ngay.
    @NonFinal
    @Value("${b2.presigned-part-url-ttl:PT30M}")
    Duration presignedPartUrlTtl;

    // VideoPlayer.tsx set thẳng hls.loadSource(src)/video.src = src (fetch trình duyệt thô, không
    // qua axiosInstance), nên URL manifest HLS trả về phải là URL tuyệt đối trỏ đúng origin gateway
    // - một path tương đối sẽ bị trình duyệt resolve nhầm sang origin của chính frontend.
    @NonFinal
    @Value("${app.gateway-public-url:http://localhost:8888/api/v1}")
    String gatewayPublicUrl;

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

    // Số part upload song song cho mỗi file trong uploadInSelfDrivenMultipart(). Cũng là kích thước
    // queue của executor bên dưới -> tổng số part được đọc/giữ trong RAM cùng lúc tối đa ~2x giá trị này.
    @NonFinal
    @Value("${b2.multipart.concurrency:6}")
    int multipartConcurrency;

    // Ngưỡng để quyết định PutObject 1 lần hay Multipart Upload cho luồng upload không-chunk.
    static final long MULTIPART_THRESHOLD = 20L * 1024 * 1024;
    // Kích thước part cho uploadInSelfDrivenMultipart() được tính động theo file size (xem computePartSize),
    // dao động trong khoảng [MIN_PART_SIZE, MAX_PART_SIZE] để cân bằng số round-trip và RAM/part.
    // S3-compatible API (bao gồm B2) yêu cầu mỗi part >= 5MB (trừ part cuối) và tối đa 10.000 part/upload.
    static final long MIN_SELF_DRIVEN_PART_SIZE = 8L * 1024 * 1024;
    static final long MAX_SELF_DRIVEN_PART_SIZE = 64L * 1024 * 1024;
    static final int TARGET_SELF_DRIVEN_PART_COUNT = 400;
    // CopyObject 1 lần của S3 API chỉ nhận object nguồn tối đa 5GiB, để margin an toàn dưới ranh giới đó.
    static final long COPY_OBJECT_MAX_SIZE = 4_500_000_000L;
    // Kích thước mỗi part khi phải multipart-copy (UploadPartCopy) cho object vượt COPY_OBJECT_MAX_SIZE.
    static final long COPY_PART_SIZE = 1024L * 1024 * 1024;
    // B2 thỉnh thoảng trả 500 "InternalError" (kèm incident id) ở CompleteMultipartUpload dù các part
    // đã upload thành công, hoặc trả 200 cho CompleteMultipartUpload nhưng object vẫn chưa "hiện" kịp
    // cho HeadObject ngay sau đó (object lớn, backend B2 cần thêm thời gian assemble các part) - các
    // lần retry nội bộ của AWS SDK bắn liên tiếp trong vài ms nên không đủ thời gian cho backend B2 kịp
    // hồi, còn nếu abort ngay thì user phải upload lại từ đầu file có thể vài GB. Retry thêm vài lần với
    // delay ở tầng app trước khi coi là fail thật.
    static final int COMPLETE_MULTIPART_MAX_ATTEMPTS = 3;
    static final Duration COMPLETE_MULTIPART_RETRY_DELAY = Duration.ofSeconds(2);

    // fileName/contentType/fileSize được chốt ngay ở bước init (đã validate trước khi phát presigned URL
    // - xem initPresignedUpload) nên completePresignedUpload không cần nhận lại chúng từ client nữa.
    // s3UploadId null nghĩa là phiên này KHÔNG phải multipart trên B2 (xem initPresignedUpload) -
    // completePresignedUpload khi đó bỏ qua bước completeMultipartUpload.
    private record PresignedMultipartSession(String key, String s3UploadId, String contentType, String fileName,
                                              long fileSize, boolean enableHls, Long durationSeconds) {
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
            // false: luồng relay-upload (POST /media/upload) dùng cho ảnh/video ngắn (vd chat) -
            // không có cách nào (và không cần) bật HLS ở đây, xem InitPresignedUploadRequest.enableHls.
            FileMgmt fileMgmt = buildFileMgmt(key, contentType, multipartFile.getSize(),
                    multipartFile.getOriginalFilename(), ownerId, bytesForProbing, false, null);
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

    // Tính part size động theo tổng kích thước file: nhắm ~TARGET_SELF_DRIVEN_PART_COUNT part/upload,
    // kẹp trong [MIN_SELF_DRIVEN_PART_SIZE, MAX_SELF_DRIVEN_PART_SIZE] rồi làm tròn lên bội số 1MB.
    // File càng lớn -> part càng lớn -> ít round-trip hơn, nhưng không bao giờ vượt mức RAM/part tối đa.
    static long computeSelfDrivenPartSize(long fileSize) {
        long size = fileSize / TARGET_SELF_DRIVEN_PART_COUNT;
        size = Math.max(size, MIN_SELF_DRIVEN_PART_SIZE);
        size = Math.min(size, MAX_SELF_DRIVEN_PART_SIZE);
        long oneMb = 1024 * 1024;
        return ((size + oneMb - 1) / oneMb) * oneMb;
    }

    private void uploadInSelfDrivenMultipart(MultipartFile file, String key, String contentType) throws IOException {
        int partSize = (int) computeSelfDrivenPartSize(file.getSize());
        String s3UploadId = createMultipartUpload(key, contentType);

        // corePoolSize = maxPoolSize = multipartConcurrency: số part upload thật sự song song.
        // Queue bị chặn ở cùng kích thước đó nên khi đầy, CallerRunsPolicy bắt chính luồng đọc file
        // upload part đó luôn (thay vì ném RejectedExecutionException hoặc buffer thêm part vào RAM
        // không giới hạn) -> tổng số part đang giữ trong bộ nhớ luôn bị chặn ở tối đa ~2x concurrency.
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                multipartConcurrency, multipartConcurrency,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(multipartConcurrency),
                new ThreadPoolExecutor.CallerRunsPolicy());

        // Lỗi từ bất kỳ part nào cũng được gom vào đây thay vì cho CompletableFuture#join ném thẳng ra
        // (join sẽ bọc nó trong CompletionException) - nhờ vậy chỉ có đúng 1 nơi gọi abort, không có
        // race giữa nhiều luồng cùng phát hiện lỗi và cùng gọi abortMultipartUploadQuietly.
        AtomicReference<Throwable> uploadFailure = new AtomicReference<>();
        List<CompletableFuture<CompletedPart>> futures = new ArrayList<>();

        try (InputStream in = file.getInputStream()) {
            int partNumber = 1;
            int read;
            byte[] buffer;
            // Dừng đọc/submit part mới ngay khi phát hiện lỗi, thay vì đọc hết cả file rồi mới biết
            // có part fail (lãng phí băng thông/RAM cho các part chắc chắn sẽ bị abort).
            while (uploadFailure.get() == null && (read = readFully(in, buffer = new byte[partSize])) > 0) {
                byte[] partData = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                int currentPartNumber = partNumber++;
                futures.add(CompletableFuture
                        .supplyAsync(() -> uploadPart(key, s3UploadId, currentPartNumber, partData), executor)
                        .exceptionally(ex -> {
                            uploadFailure.compareAndSet(null, unwrapCompletionCause(ex));
                            return null;
                        }));
            }

            // An toàn để join vô điều kiện: mọi exception đã bị "nuốt" vào uploadFailure ở exceptionally()
            // phía trên, nên các future ở đây luôn hoàn tất bình thường (thành công hoặc trả về null).
            futures.forEach(CompletableFuture::join);

            Throwable failure = uploadFailure.get();
            if (failure instanceof IOException ioe) {
                throw ioe;
            }
            if (failure instanceof RuntimeException re) {
                throw re;
            }

            List<CompletedPart> completedParts = futures.stream().map(CompletableFuture::join).toList();
            completeMultipartUpload(key, s3UploadId, completedParts);
            executor.shutdown();
        } catch (IOException | RuntimeException e) {
            // RuntimeException (không chỉ SdkException) để đảm bảo multipart luôn được abort trên B2
            // ngay cả khi lỗi không lường trước xảy ra bên trong 1 trong các task upload part song song.
            executor.shutdownNow();
            abortMultipartUploadQuietly(key, s3UploadId);
            throw e;
        }
    }

    private static Throwable unwrapCompletionCause(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
    }

    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        int read;
        while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
            total += read;
        }
        return total;
    }

    // Áp circuit breaker + rate limiter "b2" theo kiểu lập trình thay vì @CircuitBreaker/@RateLimiter
    // annotation: mọi phương thức gọi B2 ở dưới đều private và luôn được gọi bằng self-invocation
    // (this.foo(...) từ method khác trong cùng class) - Spring AOP chỉ chặn được lời gọi đi qua proxy
    // của bean nên annotation trên method private không bao giờ có tác dụng (im lặng bỏ qua, không lỗi).
    // Rate limiter nằm ngoài circuit breaker để 1 lần bị từ chối do rate limit không bị tính là 1 lần
    // gọi B2 thất bại vào sliding window của breaker.
    private <T> T callB2(Supplier<T> operation) {
        Supplier<T> withCircuitBreaker = CircuitBreaker.decorateSupplier(
                circuitBreakerRegistry.circuitBreaker("b2"), operation);
        return RateLimiter.decorateSupplier(
                rateLimiterRegistry.rateLimiter("b2"), withCircuitBreaker).get();
    }

    private void callB2(Runnable operation) {
        callB2(() -> {
            operation.run();
            return null;
        });
    }

    private void putObject(String key, String contentType, byte[] data) {
        callB2(() -> s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data)));
    }

    private String createMultipartUpload(String key, String contentType) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        return callB2(() -> s3Client.createMultipartUpload(request).uploadId());
    }

    private CompletedPart uploadPart(String key, String s3UploadId, int partNumber, byte[] data) {
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(s3UploadId)
                .partNumber(partNumber)
                .build();
        String eTag = callB2(() -> s3Client.uploadPart(request, RequestBody.fromBytes(data)).eTag());
        return CompletedPart.builder().partNumber(partNumber).eTag(eTag).build();
    }

    private void completeMultipartUpload(String key, String s3UploadId, List<CompletedPart> parts) {
        List<CompletedPart> sorted = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .toList();
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(s3UploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(sorted).build())
                .build();

        retryOnSdkException("completeMultipartUpload key=" + key + " uploadId=" + s3UploadId,
                () -> callB2(() -> s3Client.completeMultipartUpload(request)));
    }

    // Retry vài lần với delay cho các lệnh gọi B2 flaky ngay sau khi 1 multipart upload lớn vừa hoàn tất
    // (xem giải thích ở COMPLETE_MULTIPART_MAX_ATTEMPTS) trước khi coi là fail thật.
    private <T> T retryOnSdkException(String opDescription, Supplier<T> operation) {
        for (int attempt = 1; attempt <= COMPLETE_MULTIPART_MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (SdkException e) {
                if (attempt == COMPLETE_MULTIPART_MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("{} attempt {}/{} failed, retrying",
                        opDescription, attempt, COMPLETE_MULTIPART_MAX_ATTEMPTS, e);
                try {
                    Thread.sleep(COMPLETE_MULTIPART_RETRY_DELAY.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IllegalStateException("unreachable");
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
        validateFile(file.getContentType(), file.getSize());
    }

    // Tách riêng khỏi validateFile(MultipartFile) để initPresignedUpload() có thể validate content-type/size
    // NGAY KHI NHẬN REQUEST, trước khi phát bất kỳ presigned URL nào - vì một khi URL đã được ký, backend
    // không còn cách nào chặn được B2 nhận dữ liệu (S3 không hỗ trợ ràng buộc size/content-type qua chữ ký
    // presigned PUT theo cách SDK này dùng).
    private void validateFile(String contentType, long size) {
        if (contentType == null || contentType.isBlank()) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

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
                                    String ownerId, byte[] bytesForProbing, boolean enableHls,
                                    Long durationSeconds) {
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
            // B2/S3 không tự trích metadata video. Duration được trình duyệt đọc từ chính file đã
            // chọn và gửi ở bước init; resolution/bitrate vẫn để trống cho tới khi có ffprobe.
            return VideoFile.builder()
                    .id(key)
                    .ownerId(ownerId)
                    .contentType(contentType)
                    .size(fileSize)
                    .path(key)
                    .originalName(originalName)
                    .duration(durationSeconds)
                    // null (không phải enableHls) khi không bật cờ, để phân biệt rõ với doc cũ trước
                    // khi có HLS - cả 2 đều nghĩa là "luôn fallback MP4", nhưng PENDING chỉ dành cho
                    // video thực sự cần HlsTranscodeJob nhặt lên.
                    .hlsStatus(enableHls ? HlsStatus.PENDING : null)
                    .build();
        }
    }

    private FileResponse mapToFileResponse(FileMgmt fileMgmt) {
        FileResponse.FileResponseBuilder builder = FileResponse.builder()
                .id(fileMgmt.getId())
                .type(fileMgmt.getContentType())
                .size(fileMgmt.getSize());

        if (fileMgmt instanceof ImageFile imageFile) {
            builder.url(resolvePublicUrl(fileMgmt.getPath(), fileMgmt.getContentType()))
                    .width(imageFile.getWidth())
                    .height(imageFile.getHeight())
                    .format(imageFile.getFormat());
        } else if (fileMgmt instanceof VideoFile videoFile) {
            // HLS sẵn sàng -> trả URL manifest (segment riêng, xem getHlsPlaylist/presignHlsSegment).
            // Mọi trường hợp khác (chưa bật HLS, đang PENDING/PROCESSING, hoặc FAILED) fallback về
            // đúng hành vi cũ: URL MP4 presigned trực tiếp - video luôn xem được ngay cả khi HLS
            // chưa xong hoặc không bao giờ xong (vd codec không remux được).
            String url = videoFile.getHlsStatus() == HlsStatus.READY
                    ? hlsPlaylistUrl(videoFile.getId())
                    : resolvePublicUrl(fileMgmt.getPath(), fileMgmt.getContentType());
            builder.url(url)
                    .duration(videoFile.getDuration())
                    .resolution(videoFile.getResolution());
        } else {
            builder.url(resolvePublicUrl(fileMgmt.getPath(), fileMgmt.getContentType()));
        }

        return builder.build();
    }

    private String hlsPlaylistUrl(String fileId) {
        return gatewayPublicUrl + "/files/media/hls/" + HlsKeys.encodeFileId(fileId) + "/playlist.m3u8";
    }

    // Trả manifest gốc y nguyên, KHÔNG rewrite URL segment bên trong - các dòng segment vẫn là tên
    // file tương đối (vd "segment00000.ts"), nhờ vậy trình duyệt/hls.js tự resolve chúng thành
    // request tới đúng route /media/hls/{encodedFileId}/{segmentFile} bên dưới mà không cần loader
    // tuỳ biến nào ở frontend.
    public String getHlsPlaylist(String fileId) {
        requireReadyVideo(fileId);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(HlsKeys.playlistKeyFor(fileId))
                .build();
        return callB2(() -> s3Client.getObjectAsBytes(request).asUtf8String());
    }

    // Ký presigned URL MỚI cho đúng 1 segment tại thời điểm request tới, thay vì nhúng sẵn URL đã ký
    // vào manifest lúc trả về - nhờ vậy video dài bao lâu cũng không bao giờ gặp lại lỗi cũ (URL hết
    // hạn sau 1h dù người xem chưa xem tới đó), vì mỗi segment chỉ được ký khi thực sự được yêu cầu.
    public URI presignHlsSegment(String fileId, String segmentFile) {
        requireReadyVideo(fileId);
        GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(HlsKeys.segmentKeyFor(fileId, segmentFile))
                .responseContentType("video/mp2t");
        String url = callB2(() -> s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(presignedUrlTtl)
                        .getObjectRequest(getObjectRequest.build())
                        .build())
                .url()
                .toString());
        return URI.create(url);
    }

    private VideoFile requireReadyVideo(String fileId) {
        FileMgmt fileMgmt = fileMgmtRepository.findById(fileId).orElseThrow(() ->
                new AppException(ErrorCode.FILE_NOT_FOUND));
        if (!(fileMgmt instanceof VideoFile video) || video.getHlsStatus() != HlsStatus.READY) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        return video;
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

    // Khởi tạo multipart upload trên B2 và ký sẵn 1 presigned PUT URL cho từng part. Client sau đó PUT
    // trực tiếp từng part lên B2 bằng các URL này - file-service không nhận/relay byte nào của file,
    // chỉ điều phối (init/complete) nên không còn là bottleneck băng thông/connection-pool như luồng
    // chunked-upload cũ (uploadChunk() đã bị xoá).
    public InitPresignedUploadResponse initPresignedUpload(InitPresignedUploadRequest request) {
        validateFile(request.getContentType(), request.getFileSize());

        String key = buildObjectKey(request.getFileName());
        try {
            long partSize = computeSelfDrivenPartSize(request.getFileSize());
            int totalParts = (int) Math.ceil((double) request.getFileSize() / partSize);
            String uploadId = UUID.randomUUID().toString();
            List<PresignedPartResponse> parts;

            if (totalParts <= 1) {
                // B2 yêu cầu multipart/large-file upload phải có >= 2 part - complete với đúng 1 part
                // bị B2 từ chối ở tầng native (lỗi "large files must have at least 2 parts"), lớp
                // tương thích S3 của B2 không có mã lỗi S3 chuẩn cho việc này nên trả về 500 chung
                // chung ở bước completeMultipartUpload. Vì computeSelfDrivenPartSize() làm tròn part
                // size lên tối thiểu MIN_SELF_DRIVEN_PART_SIZE, mọi file <= mức đó luôn rơi vào
                // trường hợp 1 part -> dùng thẳng 1 presigned PUT thay vì tạo multipart upload trên B2.
                parts = List.of(PresignedPartResponse.builder()
                        .partNumber(1)
                        .url(presignPutObject(key))
                        .build());
                presignedUploadSessions.put(uploadId, new PresignedMultipartSession(
                        key, null, request.getContentType(), request.getFileName(), request.getFileSize(),
                        request.isEnableHls(), request.getDurationSeconds()));
            } else {
                String s3UploadId = createMultipartUpload(key, request.getContentType());
                parts = new ArrayList<>(totalParts);
                for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
                    parts.add(PresignedPartResponse.builder()
                            .partNumber(partNumber)
                            .url(presignUploadPart(key, s3UploadId, partNumber))
                            .build());
                }
                presignedUploadSessions.put(uploadId, new PresignedMultipartSession(
                        key, s3UploadId, request.getContentType(), request.getFileName(), request.getFileSize(),
                        request.isEnableHls(), request.getDurationSeconds()));
            }

            return InitPresignedUploadResponse.builder()
                    .uploadId(uploadId)
                    .partSize(partSize)
                    .parts(parts)
                    .build();
        } catch (SdkException e) {
            log.error("Error initializing presigned upload on B2", e);
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    private String presignUploadPart(String key, String s3UploadId, int partNumber) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(s3UploadId)
                .partNumber(partNumber)
                .build();
        return callB2(() -> s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                        .signatureDuration(presignedPartUrlTtl)
                        .uploadPartRequest(uploadPartRequest)
                        .build())
                .url()
                .toString());
    }

    // Presigned PUT thẳng cho trường hợp file chỉ có 1 "part" (xem initPresignedUpload) - không set
    // content-type trên request để chữ ký không ràng buộc client phải gửi đúng header đó, giữ đúng
    // hành vi hiện tại của presigned part URL (client chỉ PUT body, không set thêm header nào).
    private String presignPutObject(String key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return callB2(() -> s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(presignedPartUrlTtl)
                        .putObjectRequest(putObjectRequest)
                        .build())
                .url()
                .toString());
    }

    @Transactional
    public FileResponse completePresignedUpload(CompletePresignedUploadRequest request) {
        PresignedMultipartSession session = presignedUploadSessions.remove(request.getUploadId());
        if (session == null) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }

        List<CompletedPart> completedParts = session.s3UploadId() != null
                ? request.getParts().stream()
                        .map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getETag()).build())
                        .toList()
                : List.of();

        // Chặn sớm khi client gửi thiếu eTag (thường do bucket B2 chưa expose header "ETag" qua CORS,
        // hoặc trình duyệt đang chạy bundle cũ chưa có guard check ETag trước khi PUT part) - B2 luôn
        // từ chối completeMultipartUpload trong trường hợp này bằng 1 lỗi 500 "InternalError" chung
        // chung y hệt lỗi B2 flaky thật, khiến completeMultipartUpload() retry 3 lần vô ích (~10-15s)
        // trước khi vẫn fail. Validate ở đây để fail ngay, rõ ràng, không tốn round-trip tới B2.
        if (session.s3UploadId() != null) {
            boolean hasMissingETag = completedParts.isEmpty()
                    || completedParts.stream().anyMatch(p -> p.eTag() == null || p.eTag().isBlank());
            if (hasMissingETag) {
                log.error("Rejecting completePresignedUpload {} (key={}, uploadId={}): missing ETag on one or " +
                                "more parts: {}", request.getUploadId(), session.key(), session.s3UploadId(),
                        completedParts.stream().map(p -> p.partNumber() + ":" + p.eTag()).toList());
                abortMultipartUploadQuietly(session.key(), session.s3UploadId());
                throw new AppException(ErrorCode.INVALID_UPLOAD_PARTS);
            }
        }

        boolean multipartCompleted = false;
        try {
            if (session.s3UploadId() != null) {
                completeMultipartUpload(session.key(), session.s3UploadId(), completedParts);
                multipartCompleted = true;
            }
            // else: phiên single-PUT (xem initPresignedUpload) - object đã được ghi đầy đủ ngay từ
            // request PUT của client, không có gì để "complete" trên B2.
            long size = headObjectSize(session.key());

            String finalKey = session.key();
            if (bucketPublic) {
                // Bucket Public: GET ẩn danh không cho override response header, nên phải sửa
                // content-type thật trên object (đổi luôn key cho có phần mở rộng cho gọn).
                finalKey = finalizeObjectKeyAndContentType(session.key(), session.fileName(), session.contentType(), size);
            }
            // Bucket Private: giữ nguyên key tạm - content-type đúng được ép lúc ký presigned URL
            // (xem resolvePublicUrl), không cần đụng tới object.

            String ownerId = SecurityUtils.getCurrentUserId();
            FileMgmt fileMgmt = buildFileMgmt(finalKey, session.contentType(), size, session.fileName(), ownerId,
                    null, session.enableHls(), session.durationSeconds());
            fileMgmtRepository.save(fileMgmt);

            return mapToFileResponse(fileMgmt);

        } catch (SdkException e) {
            // Log toàn bộ partNumber:eTag đã submit khi fail - đây là dữ liệu duy nhất giúp phân biệt
            // B2 flaky thật (parts hợp lệ, retry sẽ qua) với lỗi cấu trúc phía client (thiếu part, trùng
            // partNumber, eTag sai định dạng...) mà B2 cũng trả về 500 chung chung như lỗi flaky.
            String partsDump = completedParts.stream().map(p -> p.partNumber() + ":" + p.eTag()).toList().toString();
            if (e instanceof AwsServiceException ase) {
                log.error("Error completing presigned upload {} (key={}, uploadId={}, partCount={}, parts={}): " +
                                "httpStatus={}, errorCode={}, errorMessage={}, requestId={}",
                        request.getUploadId(), session.key(), session.s3UploadId(), completedParts.size(), partsDump,
                        ase.statusCode(), ase.awsErrorDetails().errorCode(), ase.awsErrorDetails().errorMessage(),
                        ase.requestId(), ase);
            } else {
                log.error("Error completing presigned upload {} (key={}, uploadId={}, partCount={}, parts={})",
                        request.getUploadId(), session.key(), session.s3UploadId(), completedParts.size(), partsDump, e);
            }
            // Nếu completeMultipartUpload đã thành công thì uploadId không còn tồn tại để abort nữa
            // (B2 luôn trả NoSuchUpload cho abort sau khi complete) - object thật đã ghi xong trên B2,
            // chỉ là headObject/finalize sau đó lỗi, nên bỏ qua bước abort để log không gây hiểu nhầm.
            if (session.s3UploadId() != null && !multipartCompleted) {
                abortMultipartUploadQuietly(session.key(), session.s3UploadId());
            }
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

    private CompletedPart uploadPartCopy(String sourceKey, String destKey, String uploadId, int partNumber,
                                          long rangeStart, long rangeEnd) {
        UploadPartCopyResponse response = callB2(() -> s3Client.uploadPartCopy(UploadPartCopyRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(bucketName)
                .destinationKey(destKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .copySourceRange("bytes=" + rangeStart + "-" + rangeEnd)
                .build()));
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.copyPartResult().eTag())
                .build();
    }

    private void copyObject(String sourceKey, String destKey, String contentType) {
        callB2(() -> s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(sourceKey)
                .destinationBucket(bucketName)
                .destinationKey(destKey)
                .contentType(contentType)
                .metadataDirective(MetadataDirective.REPLACE)
                .build()));
    }

    private void deleteObject(String key) {
        callB2(() -> s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build()));
    }

    private long headObjectSize(String key) {
        return retryOnSdkException("headObject key=" + key, () ->
                callB2(() -> s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build())
                        .contentLength()));
    }

    public FileResponse getFileInfo(String fileId) {
        FileMgmt fileMgmt = fileMgmtRepository.findById(fileId).orElseThrow(() ->
                new AppException(ErrorCode.FILE_NOT_FOUND));
        return mapToFileResponse(fileMgmt);
    }
}
