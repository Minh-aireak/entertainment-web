package com.MyProject.profile.profile_service.repository.httpClient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.profile.profile_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.profile.profile_service.dto.response.FileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-profile-client", url = "${app.services.file.url}",
        configuration = { AuthenticationRequestInterceptor.class})
public interface FileClient {
    @PostMapping(value = "/files/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<FileResponse> uploadAvatar(@RequestPart("file") MultipartFile multipartFile);

    @GetMapping("/files/media/info/{fileId}")
    ApiResponse<FileResponse> getFileInfo(@PathVariable("fileId") String fileId);
}
