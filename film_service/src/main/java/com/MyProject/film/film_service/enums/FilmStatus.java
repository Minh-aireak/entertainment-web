package com.MyProject.film.film_service.enums;

import java.util.Locale;

public enum FilmStatus {
    /** Phim / series đang cập nhật tập mới hoặc chưa kết thúc */
    ONGOING,
    /** Phim / series đã hoàn thành, không còn cập nhật thêm */
    COMPLETED;

    /**
     * Converts the value stored in Elasticsearch to the current domain status.
     * The legacy values are kept here only as a read-time compatibility layer;
     * they are not exposed as valid values for API requests or MySQL entities.
     */
    public static FilmStatus fromIndexedValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NOW_PLAYING", "UPCOMING" -> ONGOING;
            case "ENDED", "ARCHIVED" -> COMPLETED;
            case "ONGOING" -> ONGOING;
            case "COMPLETED" -> COMPLETED;
            default -> throw new IllegalArgumentException("Unknown indexed film status: " + value);
        };
    }
}
