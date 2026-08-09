package com.MyProject.file.file_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.file.file_service.dto.request.CompletePresignedUploadRequest;
import com.MyProject.file.file_service.dto.request.CompletedPartRequest;
import com.MyProject.file.file_service.dto.request.InitPresignedUploadRequest;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.dto.response.InitPresignedUploadResponse;
import com.MyProject.file.file_service.entity.FileMgmt;
import com.MyProject.file.file_service.enums.ErrorCode;
import com.MyProject.file.file_service.exception.AppException;
import com.MyProject.file.file_service.repository.FileMgmtRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * s3Presigner is a REAL S3Presigner (dummy credentials, endpoint override) instead of a mock:
 * presigning is pure local SigV4 computation (no network call), and its result classes expose
 * only a `final url()` method that Mockito's inline mock maker cannot stub.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    S3Client s3Client;

    @Mock
    FileMgmtRepository fileMgmtRepository;

    S3Presigner s3Presigner;
    FileService fileService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        s3Presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
                .endpointOverride(URI.create("https://s3.test.com"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        fileService = new FileService(s3Client, s3Presigner, fileMgmtRepository,
                CircuitBreakerRegistry.ofDefaults(), RateLimiterRegistry.ofDefaults());
        ReflectionTestUtils.setField(fileService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(fileService, "endpoint", "s3.test.com");
        ReflectionTestUtils.setField(fileService, "bucketPublic", false);
        ReflectionTestUtils.setField(fileService, "presignedUrlTtl", Duration.ofHours(1));
        ReflectionTestUtils.setField(fileService, "presignedPartUrlTtl", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(fileService, "maxImageSize", DataSize.ofMegabytes(25));
        ReflectionTestUtils.setField(fileService, "maxVideoSize", DataSize.ofGigabytes(5));
        ReflectionTestUtils.setField(fileService, "maxRawSize", DataSize.ofMegabytes(100));
        ReflectionTestUtils.setField(fileService, "multipartConcurrency", 6);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn("user-1");

        lenient().when(fileMgmtRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
        s3Presigner.close();
    }

    // ---------- uploadFile ----------

    @Test
    void uploadFile_happyPath_smallFile_putsObjectAndSavesMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "small-bytes".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        FileResponse response = fileService.uploadFile(file);

        assertThat(response.getType()).isEqualTo("image/jpeg");
        assertThat(response.getUrl()).contains("test-bucket").contains("X-Amz-Signature");
        assertThat(response.getSize()).isEqualTo(file.getSize());
        verify(fileMgmtRepository).save(any(FileMgmt.class));
    }

    @Test
    void uploadFile_blankContentType_throwsInvalidFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", null, "bytes".getBytes());

        assertThatThrownBy(() -> fileService.uploadFile(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadFile_imageExceedsMaxSize_throwsFileTooLarge() {
        ReflectionTestUtils.setField(fileService, "maxImageSize", DataSize.ofBytes(4));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "way-too-big".getBytes());

        assertThatThrownBy(() -> fileService.uploadFile(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadFile_ioExceptionReadingBytes_throwsUploadError() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(100L);
        when(file.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> fileService.uploadFile(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_ERROR);
    }

    @Test
    void uploadFile_sdkExceptionFromS3_throwsUploadError() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(SdkException.builder().message("B2 unavailable").build());

        assertThatThrownBy(() -> fileService.uploadFile(file))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_ERROR);
        verifyNoInteractions(fileMgmtRepository);
    }

    // ---------- computeSelfDrivenPartSize (pure function) ----------

    @Test
    void computeSelfDrivenPartSize_smallFile_clampsToMinPartSize() {
        long partSize = FileService.computeSelfDrivenPartSize(1L * 1024 * 1024);
        assertThat(partSize).isEqualTo(FileService.MIN_SELF_DRIVEN_PART_SIZE);
    }

    @Test
    void computeSelfDrivenPartSize_hugeFile_clampsToMaxPartSize() {
        long partSize = FileService.computeSelfDrivenPartSize(100L * 1024 * 1024 * 1024);
        assertThat(partSize).isEqualTo(FileService.MAX_SELF_DRIVEN_PART_SIZE);
    }

    @Test
    void computeSelfDrivenPartSize_midSizeFile_roundsUpToNearestMb() {
        long fileSize = 400L * 10 * 1024 * 1024;
        long partSize = FileService.computeSelfDrivenPartSize(fileSize);
        assertThat(partSize).isEqualTo(10L * 1024 * 1024);
        assertThat(partSize % (1024 * 1024)).isZero();
    }

    // ---------- initPresignedUpload ----------

    @Test
    void initPresignedUpload_smallFile_returnsSinglePresignedPutPart() {
        InitPresignedUploadRequest request = InitPresignedUploadRequest.builder()
                .fileName("clip.mp4").contentType("video/mp4").fileSize(5L * 1024 * 1024).build();

        InitPresignedUploadResponse response = fileService.initPresignedUpload(request);

        assertThat(response.getParts()).hasSize(1);
        assertThat(response.getParts().get(0).getUrl()).contains("test-bucket");
        verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void initPresignedUpload_largeFile_returnsMultiplePresignedParts() {
        InitPresignedUploadRequest request = InitPresignedUploadRequest.builder()
                .fileName("archive.bin").contentType("application/octet-stream")
                .fileSize(100L * 1024 * 1024).build();
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("s3-upload-1").build());

        InitPresignedUploadResponse response = fileService.initPresignedUpload(request);

        assertThat(response.getParts().size()).isGreaterThan(1);
        assertThat(response.getParts()).allSatisfy(part -> assertThat(part.getUrl()).contains("test-bucket"));
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void initPresignedUpload_invalidContentType_throwsBeforeContactingS3() {
        InitPresignedUploadRequest request = InitPresignedUploadRequest.builder()
                .fileName("clip.mp4").contentType("").fileSize(5L * 1024 * 1024).build();

        assertThatThrownBy(() -> fileService.initPresignedUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    void initPresignedUpload_sdkExceptionOnCreateMultipart_throwsUploadError() {
        InitPresignedUploadRequest request = InitPresignedUploadRequest.builder()
                .fileName("archive.bin").contentType("application/octet-stream")
                .fileSize(100L * 1024 * 1024).build();
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenThrow(SdkException.builder().message("B2 unavailable").build());

        assertThatThrownBy(() -> fileService.initPresignedUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_ERROR);
    }

    // ---------- completePresignedUpload ----------

    @Test
    void completePresignedUpload_unknownUploadId_throwsSessionNotFound() {
        CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
                .uploadId("does-not-exist").parts(List.of()).build();

        assertThatThrownBy(() -> fileService.completePresignedUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
    }

    @Test
    void completePresignedUpload_multipartSessionMissingETag_abortsAndThrows() {
        String uploadId = initMultipartSession();
        when(s3Client.abortMultipartUpload(any(AbortMultipartUploadRequest.class)))
                .thenReturn(AbortMultipartUploadResponse.builder().build());
        CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
                .uploadId(uploadId)
                .parts(List.of(CompletedPartRequest.builder().partNumber(1).eTag(null).build()))
                .build();

        assertThatThrownBy(() -> fileService.completePresignedUpload(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_UPLOAD_PARTS);
        verify(s3Client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void completePresignedUpload_multipartSessionHappyPath_completesAndSaves() {
        String uploadId = initMultipartSession();
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L * 1024 * 1024).build());

        CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
                .uploadId(uploadId)
                .parts(List.of(
                        CompletedPartRequest.builder().partNumber(1).eTag("etag-1").build(),
                        CompletedPartRequest.builder().partNumber(2).eTag("etag-2").build(),
                        CompletedPartRequest.builder().partNumber(3).eTag("etag-3").build()))
                .build();

        FileResponse response = fileService.completePresignedUpload(request);

        assertThat(response.getSize()).isEqualTo(100L * 1024 * 1024);
        verify(fileMgmtRepository).save(any(FileMgmt.class));
    }

    @Test
    void completePresignedUpload_singlePutSession_skipsCompleteMultipartCall() {
        InitPresignedUploadRequest initRequest = InitPresignedUploadRequest.builder()
                .fileName("clip.mp4").contentType("video/mp4").fileSize(5L * 1024 * 1024).build();
        String uploadId = fileService.initPresignedUpload(initRequest).getUploadId();

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(5L * 1024 * 1024).build());

        CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
                .uploadId(uploadId).parts(List.of()).build();

        FileResponse response = fileService.completePresignedUpload(request);

        assertThat(response).isNotNull();
        verify(s3Client, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void completePresignedUpload_bucketPublic_finalizesKeyViaCopyObject() {
        ReflectionTestUtils.setField(fileService, "bucketPublic", true);
        InitPresignedUploadRequest initRequest = InitPresignedUploadRequest.builder()
                .fileName("clip.mp4").contentType("video/mp4").fileSize(5L * 1024 * 1024).build();
        String uploadId = fileService.initPresignedUpload(initRequest).getUploadId();

        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(5L * 1024 * 1024).build());
        when(s3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(CopyObjectResponse.builder().build());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        CompletePresignedUploadRequest request = CompletePresignedUploadRequest.builder()
                .uploadId(uploadId).parts(List.of()).build();

        fileService.completePresignedUpload(request);

        verify(s3Client).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    private String initMultipartSession() {
        InitPresignedUploadRequest request = InitPresignedUploadRequest.builder()
                .fileName("archive.bin").contentType("application/octet-stream")
                .fileSize(100L * 1024 * 1024).build();
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("s3-upload-1").build());
        InitPresignedUploadResponse response = fileService.initPresignedUpload(request);
        reset(s3Client);
        return response.getUploadId();
    }

    // ---------- getFileInfo ----------

    @Test
    void getFileInfo_found_returnsResponse() {
        FileMgmt fileMgmt = com.MyProject.file.file_service.entity.VideoFile.builder()
                .id("movie-platform/abc.mp4").contentType("video/mp4").size(1024L).path("movie-platform/abc.mp4")
                .build();
        when(fileMgmtRepository.findById("movie-platform/abc.mp4")).thenReturn(Optional.of(fileMgmt));

        FileResponse response = fileService.getFileInfo("movie-platform/abc.mp4");

        assertThat(response.getId()).isEqualTo("movie-platform/abc.mp4");
    }

    @Test
    void getFileInfo_notFound_throwsFileNotFound() {
        when(fileMgmtRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileInfo("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }
}
