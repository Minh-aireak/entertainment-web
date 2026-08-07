package com.MyProject.chat_service.repository.httpclient;

import com.MyProject.chat_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.chat_service.dto.response.FileResponse;
import com.MyProject.common.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "chat-file-client", url = "${app.services.file.url}",
        configuration = { AuthenticationRequestInterceptor.class })
public interface FileClient {
    @GetMapping("/files/media/info/{fileId}")
    ApiResponse<FileResponse> getFileInfo(@PathVariable("fileId") String fileId);
}
