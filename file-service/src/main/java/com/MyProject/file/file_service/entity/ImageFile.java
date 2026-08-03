package com.MyProject.file.file_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "file_mgmt")
@TypeAlias("IMAGE")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageFile extends FileMgmt {
    Integer width;
    Integer height;
    String format;
    String thumbnailUrl;
}
