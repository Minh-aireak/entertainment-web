package com.MyProject.file.file_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitPresignedUploadResponse {
    String uploadId;
    Long partSize;
    List<PresignedPartResponse> parts;
}
