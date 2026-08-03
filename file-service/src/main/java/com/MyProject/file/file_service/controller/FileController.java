package com.MyProject.file.file_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.file.file_service.dto.response.FileResponse;
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
    ApiResponse<String> initChunkedUpload() {
        return ApiResponse.<String>builder()
                .result(fileService.initChunkedUpload())
                .build();
    }

    @PostMapping("/media/upload/chunk")
    ApiResponse<Void> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("file") MultipartFile file) {
        fileService.uploadChunk(uploadId, chunkIndex, file);
        return ApiResponse.<Void>builder()
                .message("Chunk " + chunkIndex + " uploaded")
                .build();
    }

    @PostMapping("/media/upload/complete")
    ApiResponse<FileResponse> completeChunkedUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName,
            @RequestParam("contentType") String contentType) {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.completeChunkedUpload(uploadId, fileName, contentType))
                .message("Upload complete!")
                .build();
    }

    @GetMapping("/media/info/{fileId}")
    ApiResponse<FileResponse> getFileInfo(@PathVariable String fileId) {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.getFileInfo(fileId))
                .build();
    }

    @GetMapping("/media/download/{fileName}")
    ResponseEntity<Resource> downloadMedia(@PathVariable String fileName) throws IOException {
        var fileDownload = fileService.downloadFile(fileName);
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
}
