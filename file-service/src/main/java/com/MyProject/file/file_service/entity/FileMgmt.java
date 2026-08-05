package com.MyProject.file.file_service.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document(collection = "file_mgmt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ImageFile.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = VideoFile.class, name = "VIDEO")
})
public abstract class FileMgmt {

    @MongoId
    String id;   // B2/S3 object key

    String ownerId;
    String contentType;
    long size;
    String path;
    String originalName;
    String md5Checksum;

    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;
}