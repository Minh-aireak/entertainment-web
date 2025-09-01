package com.MyProject.file.repository;

import com.MyProject.file.dto.FileInfo;
import com.MyProject.file.entity.FileMgmt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Repository
public class FileRepository {
    @Value("${app.file.storage-dir}")
    String storageDir;

    @Value("${app.file.download-prefix}")
    String downloadPrefix;

    public FileInfo store(MultipartFile multipartFile) throws IOException {
        Path folder = Paths.get(storageDir);
        String fileExtension = StringUtils.getFilenameExtension(multipartFile.getOriginalFilename());

        String fileName = UUID.randomUUID().toString() + (Objects.isNull(fileExtension) ? "" : "." + fileExtension);

        Path filePath = folder.resolve(fileName).normalize().toAbsolutePath();

        Files.copy(multipartFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return FileInfo.builder()
                .name(fileName)
                .contentType(multipartFile.getContentType())
                .size(multipartFile.getSize())
                .md5Checksum(DigestUtils.md5DigestAsHex(multipartFile.getInputStream()))
                .path(filePath.toString())
                .url(downloadPrefix + fileName)
                .build();
    }

    public Resource read(FileMgmt fileMgmt) throws IOException {
        var data = Files.readAllBytes(Path.of(fileMgmt.getPath()));
        return new ByteArrayResource(data);
    }
}
