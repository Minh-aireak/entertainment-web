package com.MyProject.file.file_service.service;

import com.MyProject.file.file_service.dto.response.FileDownload;
import com.MyProject.file.file_service.dto.response.FileResponse;
import com.MyProject.file.file_service.exception.AppException;
import com.MyProject.file.file_service.exception.ErrorCode;
import com.MyProject.file.file_service.mapper.FileMgmtMapper;
import com.MyProject.file.file_service.repository.FileMgmtRepository;
import com.MyProject.file.file_service.repository.FileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileService {
    FileRepository fileRepository;
    FileMgmtRepository fileMgmtRepository;
    FileMgmtMapper fileMgmtMapper;

    @Transactional
    public FileResponse uploadFile(MultipartFile multipartFile) throws IOException {
        try {
            var fileInfo = fileRepository.store(multipartFile);

            var fileMgmt = fileMgmtMapper.toFileMgmt(fileInfo);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
            fileMgmt.setOwnerId(jwt.getClaim("userId"));

            fileMgmtRepository.save(fileMgmt);
            return FileResponse.builder()
                    .url(fileInfo.getUrl())
                    .build();
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD);
        }
    }

    public FileDownload downloadFile(String fileName) throws IOException {
        var fileMgmt = fileMgmtRepository.findById(fileName).orElseThrow(() ->
                new AppException(ErrorCode.FILE_NOT_FOUND));

        var resource = fileRepository.read(fileMgmt);

        return new FileDownload(fileMgmt.getContentType(), resource);
    }
}
