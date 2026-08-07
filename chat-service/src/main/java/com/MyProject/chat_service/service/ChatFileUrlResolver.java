package com.MyProject.chat_service.service;

import com.MyProject.chat_service.repository.httpclient.FileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Bucket B2 private nên file-service chỉ trả presigned URL có hạn dùng (~1h) - message attachment
// và group avatar chỉ lưu fileId, URL hiển thị luôn được resolve mới ở đây tại thời điểm trả
// response, không bao giờ lưu cố định trong Mongo/Elasticsearch.
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatFileUrlResolver {
    FileClient fileClient;

    public String resolve(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return fileClient.getFileInfo(fileId).getResult().getUrl();
        } catch (Exception e) {
            log.warn("Failed to resolve chat file {}", fileId, e);
            return null;
        }
    }

    // Resolve song song cho nhiều fileId cùng lúc (vd. đính kèm của cả 1 trang tin nhắn) - tránh N
    // request tuần tự tới file-service.
    public Map<String, String> resolveBatch(Collection<String> fileIds) {
        Map<String, CompletableFuture<String>> futures = fileIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> CompletableFuture.supplyAsync(() -> resolve(id))));

        Map<String, String> result = new HashMap<>();
        futures.forEach((id, future) -> result.put(id, future.join()));
        return result;
    }
}
