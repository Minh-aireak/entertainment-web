-- Manual fix cho lỗi: com.mysql.cj.jdbc.exceptions.MysqlDataTruncation:
-- Data too long for column 'video_url' at row 1 (SQL Error: 1406, SQLState: 22001)
--
-- Bối cảnh: film-service KHÔNG dùng Flyway/Liquibase (không có dependency, không có
-- thư mục db/migration) — schema được Hibernate sinh/point-update qua
-- `spring.jpa.hibernate.ddl-auto: update` (xem film_service/src/main/resources/application.yaml).
-- Các cột lưu URL (video_url, thumbnail_url, trailer_url, avatar_url) trước đây không có
-- @Column nào khai báo -> Hibernate mặc định VARCHAR(255). Nhưng URL thực tế lưu vào các
-- cột này là presigned GET URL do file-service ký (S3Presigner, bucket B2 private, xem
-- file-service FileService.resolvePublicUrl) — dạng URL này luôn kèm query string chữ ký
-- (X-Amz-Signature, X-Amz-Credential, X-Amz-Date, X-Amz-Expires, ...) nên thường dài
-- 400-800+ ký tự, vượt quá 255.
--
-- Entity đã được cập nhật sang @Column(columnDefinition = "TEXT") (cùng convention với
-- Film.description). Vì ddl-auto=update không đảm bảo tự ALTER kiểu cột đã tồn tại một
-- cách nhất quán/an toàn trên mọi phiên bản Hibernate, chạy script này thủ công một lần
-- trên DB hiện có để áp dụng ngay, không cần chờ redeploy. ALTER ... MODIFY COLUMN sang
-- TEXT là thao tác an toàn, giữ nguyên toàn bộ dữ liệu hiện có (MySQL tự rewrite bảng,
-- không có bước cần cast/convert thủ công vì TEXT là tập cha của VARCHAR).
--
-- Chạy trên database: film-service

USE `film-service`;

ALTER TABLE `episodes`
    MODIFY COLUMN `video_url` TEXT NULL;

ALTER TABLE `films`
    MODIFY COLUMN `thumbnail_url` TEXT NULL,
    MODIFY COLUMN `trailer_url` TEXT NULL;

ALTER TABLE `actors`
    MODIFY COLUMN `avatar_url` TEXT NULL;

ALTER TABLE `directors`
    MODIFY COLUMN `avatar_url` TEXT NULL;
