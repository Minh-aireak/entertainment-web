package com.MyProject.post.post_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileInfoResponse {
    String id;
    String url;
    String fileName;
    String mimeType;
    Long fileSize;
}
