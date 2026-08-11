package com.MyProject.file.file_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.file.file_service.dto.request.CompletePresignedUploadRequest;
import com.MyProject.file.file_service.dto.request.InitPresignedUploadRequest;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.dto.response.InitPresignedUploadResponse;
import com.MyProject.file.file_service.enums.ErrorCode;
import com.MyProject.file.file_service.exception.AppException;
import com.MyProject.file.file_service.service.FileService;
import com.MyProject.file.file_service.service.HlsKeys;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileController {
    // Chỉ chấp nhận đúng định dạng segment do HlsTranscodeJob sinh ra (segment%05d.ts) - endpoint
    // này public/không xác thực (giống /media/info/**) nên phải chặn sớm mọi filename lạ trước khi
    // ghép thành B2 key.
    private static final Pattern HLS_SEGMENT_PATTERN = Pattern.compile("^segment\\d{5}\\.ts$");

    FileService fileService;

    @PostMapping("/media/upload")
    ApiResponse<FileResponse> upload(@RequestParam("file") MultipartFile multipartFile) {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.uploadFile(multipartFile))
                .message("Upload succeeded!")
                .build();
    }

    @PostMapping("/media/upload/init")
    ApiResponse<InitPresignedUploadResponse> initPresignedUpload(@RequestBody InitPresignedUploadRequest request) {
        return ApiResponse.<InitPresignedUploadResponse>builder()
                .result(fileService.initPresignedUpload(request))
                .build();
    }

    @PostMapping("/media/upload/complete")
    ApiResponse<FileResponse> completePresignedUpload(@RequestBody CompletePresignedUploadRequest request) {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.completePresignedUpload(request))
                .message("Upload complete!")
                .build();
    }

    // {*fileId} (không phải {fileId}) vì id ở đây chính là object key trên B2 (xem FileMgmt.id), luôn
    // chứa "/" (vd "movie-platform/uuid.mp4") - {fileId} thường chỉ khớp đúng 1 path segment nên sẽ
    // không tìm thấy handler cho id có "/", request rơi xuống .anyRequest().authenticated() và trả về
    // 401 thay vì chạy tới logic getFileInfo. {*fileId} khớp phần path còn lại (kèm "/" ở đầu, cần bỏ).
    @GetMapping("/media/info/{*fileId}")
    ApiResponse<FileResponse> getFileInfo(@PathVariable String fileId) {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.getFileInfo(stripLeadingSlash(fileId)))
                .build();
    }

    // {encodedFileId} thay vì {*fileId} vì key gốc chứa "/" (xem getFileInfo ở trên) và {*...} bắt
    // buộc phải là phần tử cuối của pattern - không thể theo sau bởi "/playlist.m3u8" hay
    // "/{segmentFile}". HlsKeys.encodeFileId gói cả key thành 1 path segment "phẳng" để né vấn đề đó.
    @GetMapping(value = "/media/hls/{encodedFileId}/playlist.m3u8", produces = "application/vnd.apple.mpegurl")
    ResponseEntity<String> getHlsPlaylist(@PathVariable String encodedFileId) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/vnd.apple.mpegurl"))
                .body(fileService.getHlsPlaylist(decodeOrThrow(encodedFileId)));
    }

    // Redirect (302) sang presigned URL B2 vừa ký cho riêng segment này thay vì proxy byte video qua
    // backend - giữ đúng nguyên tắc "backend không bao giờ relay bytes video" đã áp dụng cho luồng
    // MP4/getFileInfo hiện có.
    @GetMapping("/media/hls/{encodedFileId}/{segmentFile}")
    ResponseEntity<Void> getHlsSegment(@PathVariable String encodedFileId, @PathVariable String segmentFile) {
        if (!HLS_SEGMENT_PATTERN.matcher(segmentFile).matches()) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(fileService.presignHlsSegment(decodeOrThrow(encodedFileId), segmentFile))
                .build();
    }

    private static String decodeOrThrow(String encodedFileId) {
        try {
            return HlsKeys.decodeFileId(encodedFileId);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
