package com.MyProject.file.dto.response;

import org.springframework.core.io.Resource;

public record FileDownload(String contentType, Resource resource) {
}
