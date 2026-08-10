package com.MyProject.post.post_service.repository.httpclient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.post.post_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.post.post_service.dto.response.FileInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-file-service", url = "${app.services.file.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface FileClient {
    @GetMapping(value = "/files/media/info/{fileId}")
    ApiResponse<FileInfoResponse> getFileInfo(@PathVariable("fileId") String fileId);
}
