package com.MyProject.file.file_service.dto.response;

import org.springframework.core.io.Resource;

public record FileDownload(String contentType, Resource resource) {
}
