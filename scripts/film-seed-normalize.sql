-- Run only against the temporary film seed working database.
-- Episode video_file_id values intentionally remain NULL until the user uploads
-- the confirmed Vagabond source files and provides their real file IDs.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- These rows were reintroduced by the old always-on INSERT IGNORE seed after
-- the administrator reduced the catalogued episode count. Keep the requested
-- counts instead of bringing the stale tail back on every restart.
DELETE e
FROM episodes e
JOIN films f ON f.id = e.film_id
WHERE e.episode_number > f.episode_count
  AND f.id IN (
    'fb020000-0000-0000-0000-000000000002',
    'fa030000-0000-0000-0000-000000000003'
  );

-- Retain Vagabond's complete 16-episode catalogue. Its video sources will be
-- assigned later after episodes 10, 11 and 12 have actually been uploaded.
UPDATE films
SET episode_count = 16
WHERE id = 'f3333333-3333-3333-3333-333333333333';

-- Give every existing episode a readable Vietnamese title. A missing video has
-- duration 0; upload finalization will replace it with the measured file length.
UPDATE episodes
SET title = CASE MOD(CRC32(CONCAT(film_id, '#', episode_number)), 24)
        WHEN 0 THEN 'Khởi đầu mới'
        WHEN 1 THEN 'Dấu vết đầu tiên'
        WHEN 2 THEN 'Bí mật dần hé lộ'
        WHEN 3 THEN 'Cuộc gặp gỡ bất ngờ'
        WHEN 4 THEN 'Lời hứa còn dang dở'
        WHEN 5 THEN 'Bước ngoặt định mệnh'
        WHEN 6 THEN 'Thử thách phía trước'
        WHEN 7 THEN 'Đồng hành trong giông bão'
        WHEN 8 THEN 'Sự thật sau màn đêm'
        WHEN 9 THEN 'Cuộc đối đầu quyết định'
        WHEN 10 THEN 'Niềm tin bị thử thách'
        WHEN 11 THEN 'Lựa chọn khó khăn'
        WHEN 12 THEN 'Âm mưu trong bóng tối'
        WHEN 13 THEN 'Manh mối cuối cùng'
        WHEN 14 THEN 'Trở về từ hiểm nguy'
        WHEN 15 THEN 'Tình bạn và lòng trung thành'
        WHEN 16 THEN 'Cánh cửa quá khứ'
        WHEN 17 THEN 'Đêm trước trận chiến'
        WHEN 18 THEN 'Hy vọng giữa hỗn loạn'
        WHEN 19 THEN 'Khoảnh khắc sinh tử'
        WHEN 20 THEN 'Những người không bỏ cuộc'
        WHEN 21 THEN 'Bí mật của kẻ đối đầu'
        WHEN 22 THEN 'Hành trình tiếp diễn'
        ELSE 'Bình minh sau giông bão'
      END,
    video_file_id = NULL,
    duration_minutes = 0;

-- One episode for each standalone film.
INSERT INTO episodes (id, duration_minutes, episode_number, season_number, title, video_file_id, film_id) VALUES
  ('seed-episode-wicked-for-good', 0, 1, 1, 'Khúc ca cuối cùng của xứ Oz', NULL, 'fs080000-0000-0000-0000-000000000008'),
  ('seed-episode-spider-man-new-day', 0, 1, 1, 'Ngày mới của Người Nhện', NULL, 'fs010000-0000-0000-0000-000000000001'),
  ('seed-episode-na-tra-2', 0, 1, 2, 'Ma Đồng náo hải', NULL, 'fs020000-0000-0000-0000-000000000002'),
  ('seed-episode-mua-do', 0, 1, 1, 'Khúc tráng ca Thành cổ', NULL, 'fs070000-0000-0000-0000-000000000007'),
  ('seed-episode-avatar-fire-ash', 0, 1, 1, 'Lửa và tro tàn', NULL, 'fs050000-0000-0000-0000-000000000005'),
  ('seed-episode-michael', 0, 1, 1, 'Huyền thoại âm nhạc', NULL, 'fs030000-0000-0000-0000-000000000003'),
  ('seed-episode-tho-oi', 0, 1, 1, 'Lời gọi trong đêm', NULL, 'fs060000-0000-0000-0000-000000000006'),
  ('seed-episode-the-odyssey', 0, 1, 1, 'Hành trình trở về Ithaca', NULL, 'fs040000-0000-0000-0000-000000000004');

-- Nine pre-created episodes for Trảm Thần: Phàm Trần Thần Vực Phần 2.
INSERT INTO episodes (id, duration_minutes, episode_number, season_number, title, video_file_id, film_id) VALUES
  ('seed-tram-than-s2-01', 0, 1, 2, 'Người Gác Đêm trở lại', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-02', 0, 2, 2, 'Tín hiệu từ Thương Nam', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-03', 0, 3, 2, 'Bóng tối của Cổ Thần', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-04', 0, 4, 2, 'Cuộc hội ngộ đội 136', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-05', 0, 5, 2, 'Tà thần phương Tây', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-06', 0, 6, 2, 'Lời thề dưới màn đêm', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-07', 0, 7, 2, 'Cơn bão đang đến', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-08', 0, 8, 2, 'Cường giả hội tụ', NULL, 'f4444444-4444-4444-4444-444444444444'),
  ('seed-tram-than-s2-09', 0, 9, 2, 'Trận chiến ở Thương Nam', NULL, 'f4444444-4444-4444-4444-444444444444');

-- Recompute from episode data so the admin list, search index and watch-room
-- selector all start from one consistent value.
UPDATE films f
SET f.episode_count = (
  SELECT COALESCE(MAX(e.episode_number), 0)
  FROM episodes e
  WHERE e.film_id = f.id
);

SET FOREIGN_KEY_CHECKS = 1;
