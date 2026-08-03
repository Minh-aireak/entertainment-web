package com.MyProject.file.file_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "file_mgmt")
@TypeAlias("VIDEO")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VideoFile extends FileMgmt {
    Long duration;          // Giây
    String resolution;      // "1920x1080"
    String thumbnailUrl;    // Ảnh thumbnail của video
    Long bitrate;
}