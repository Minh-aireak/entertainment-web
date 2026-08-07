-- Manual fix cho bug: video tập phim phát được ngay sau khi upload nhưng hỏng sau đó
-- (thường lộ ra sau khi restart, vì đủ thời gian trôi qua để URL hết hạn).
--
-- Nguyên nhân: bucket B2 ở chế độ private (B2_BUCKET_PUBLIC=false), nên file-service
-- (FileService.resolvePublicUrl) trả về presigned GET URL chỉ có hiệu lực theo
-- B2_PRESIGNED_URL_TTL (mặc định 1h). Trước đây frontend (EpisodeUpload.tsx) lưu thẳng
-- URL tạm này vào cột episodes.video_url -> hết hạn là video không phát được nữa.
--
-- Fix: cột này giờ lưu file ID (object key trên B2) thay vì URL. Frontend gọi lại
-- file-service (GET /media/info/{fileId}) để lấy presigned URL mới mỗi khi phát, thay vì
-- dùng URL lưu cố định. Entity Episode.videoUrl đã đổi tên thành videoFileId; đổi tên cột
-- tương ứng ở đây vì film-service không dùng Flyway/Liquibase (ddl-auto=update không tự
-- rename cột đã tồn tại, chỉ tạo thêm cột mới rồi bỏ mồ côi cột cũ).
--
-- Dữ liệu cũ trong cột video_url toàn bộ là presigned URL (không phải file ID hợp lệ) nên
-- set về NULL luôn - giữ lại chỉ khiến file-service trả FILE_NOT_FOUND khi tra cứu. Các tập
-- phim đã upload trước đây cần được upload lại để có video_file_id hợp lệ.
--
-- Chạy trên database: film-service

USE `film-service`;

ALTER TABLE `episodes`
    CHANGE COLUMN `video_url` `video_file_id` TEXT NULL;

UPDATE `episodes` SET `video_file_id` = NULL WHERE `video_file_id` IS NOT NULL;
