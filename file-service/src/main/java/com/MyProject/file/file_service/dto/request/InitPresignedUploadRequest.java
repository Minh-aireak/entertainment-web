package com.MyProject.file.file_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitPresignedUploadRequest {
    String fileName;
    String contentType;
    Long fileSize;
}
