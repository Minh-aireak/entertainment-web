package com.MyProject.file.file_service.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Nơi duy nhất suy ra key/segment HLS trên B2 và mã hoá fileId cho URL - dùng chung giữa FileService,
// FileController và HlsTranscodeJob để tránh lệch nhau giữa nơi ghi (job) và nơi đọc (controller).
public final class HlsKeys {
    private static final String HLS_SUFFIX = ".hls/";

    private HlsKeys() {
    }

    public static String prefixFor(String originalKey) {
        return originalKey + HLS_SUFFIX;
    }

    public static String playlistKeyFor(String originalKey) {
        return prefixFor(originalKey) + "playlist.m3u8";
    }

    public static String segmentKeyFor(String originalKey, String segmentFile) {
        return prefixFor(originalKey) + segmentFile;
    }

    // fileId (= B2 object key, vd "movie-platform/uuid.mp4") luôn chứa "/" nên không thể dùng trực
    // tiếp làm 1 path segment trong route HLS - và {*fileId} (catch-all) không hợp lệ nếu có phần
    // path cố định theo sau (vd "/playlist.m3u8"), Spring PathPattern bắt buộc {*var} phải là phần
    // tử cuối cùng. Mã hoá cả key thành 1 segment path "phẳng" duy nhất để né cả 2 vấn đề.
    public static String encodeFileId(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws IllegalArgumentException nếu encoded không phải base64url hợp lệ - caller tự map sang 404.
     */
    public static String decodeFileId(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
