package com.MyProject.file.file_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.file.file_service.dto.request.CompletePresignedUploadRequest;
import com.MyProject.file.file_service.dto.request.InitPresignedUploadRequest;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.dto.response.InitPresignedUploadResponse;
import com.MyProject.file.file_service.service.FileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileController {
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

    @GetMapping("/media/download/{*fileName}")
    ResponseEntity<Resource> downloadMedia(@PathVariable String fileName) throws IOException {
        var fileDownload = fileService.downloadFile(stripLeadingSlash(fileName));
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(fileDownload.resource().getFilename())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(MediaType.parseMediaType(fileDownload.contentType()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileDownload.resource());
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
