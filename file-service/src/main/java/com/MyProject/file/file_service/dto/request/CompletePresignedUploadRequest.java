package com.MyProject.file.file_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompletePresignedUploadRequest {
    String uploadId;
    List<CompletedPartRequest> parts;
}
