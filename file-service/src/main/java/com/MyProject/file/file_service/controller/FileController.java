package com.MyProject.file.file_service.controller;

import com.MyProject.file.file_service.dto.response.ApiResponse;
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
    ApiResponse<FileResponse> uploadMedia(@RequestParam("file") MultipartFile multipartFile) throws IOException {
        return ApiResponse.<FileResponse>builder()
                .result(fileService.uploadFile(multipartFile))
                .message("Upload succeeded!")
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

        return ResponseEntity.<Resource>ok()
                .headers(headers)
                .body(fileDownload.resource());
    }
}
