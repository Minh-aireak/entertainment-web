-- Seed data for local/dev testing of the film catalog UI.
-- videoUrl / trailerUrl are placeholder strings only — real playback loading logic is not wired up yet.
-- Runs on every startup (spring.sql.init.mode=always); INSERT IGNORE / NOT EXISTS keep it idempotent.

-- Directors
INSERT IGNORE INTO directors (id, name, avatar_url, deleted) VALUES
('d1111111-1111-1111-1111-111111111111', 'Tran Minh Khoi', 'https://placehold.co/200x200?text=Director+1', false),
('d2222222-2222-2222-2222-222222222222', 'Le Hoai An', 'https://placehold.co/200x200?text=Director+2', false);

-- Actors
INSERT IGNORE INTO actors (id, name, avatar_url, deleted) VALUES
('a1111111-1111-1111-1111-111111111111', 'Nguyen Van A', 'https://placehold.co/200x200?text=Actor+1', false),
('a2222222-2222-2222-2222-222222222222', 'Tran Thi B', 'https://placehold.co/200x200?text=Actor+2', false),
('a3333333-3333-3333-3333-333333333333', 'Pham Van C', 'https://placehold.co/200x200?text=Actor+3', false);

-- Films
INSERT IGNORE INTO films (id, title, description, thumbnail_url, trailer_url, duration_minutes, release_date, last_update, series, average_rating, rating_count, follow_count, episode_count, season, director_id, country, status) VALUES
('f1111111-1111-1111-1111-111111111111', 'Vong Xoay Thoi Gian', 'Mot nha vat ly hoc vo tinh mo ra canh cua den mot chieu khong gian song song, noi anh phai doi mat voi chinh minh cua qua khu.', 'https://placehold.co/400x600?text=Vong+Xoay+Thoi+Gian', 'PLACEHOLDER_VIDEO_URL', 118, '2026-06-12 00:00:00', '2026-08-01 10:00:00', false, 4.5, 128, 340, 0, 1, 'd1111111-1111-1111-1111-111111111111', 'USA', 'NOW_PLAYING'),
('f2222222-2222-2222-2222-222222222222', 'Chuyen Tinh Mua Ha', 'Cau chuyen tinh yeu nhe nhang giua hai nguoi tre lon len o mot thi tran ven bien vao mua he cuoi cung truoc khi buoc vao doi.', 'https://placehold.co/400x600?text=Chuyen+Tinh+Mua+Ha', 'PLACEHOLDER_VIDEO_URL', 105, '2026-05-20 00:00:00', '2026-08-01 10:00:00', false, 4.2, 76, 190, 0, 1, 'd2222222-2222-2222-2222-222222222222', 'VIETNAM', 'NOW_PLAYING'),
('f3333333-3333-3333-3333-333333333333', 'Biet Doi Giai Cuu', 'Mot nhom dac nhiem duoc trieu tap de giai cuu con tin trong mot chuoi nhiem vu nghet tho xuyen nhieu quoc gia.', 'https://placehold.co/400x600?text=Biet+Doi+Giai+Cuu', 'PLACEHOLDER_VIDEO_URL', 45, '2026-04-10 00:00:00', '2026-08-01 10:00:00', true, 4.7, 214, 512, 3, 1, 'd1111111-1111-1111-1111-111111111111', 'KOREA', 'NOW_PLAYING'),
('f4444444-4444-4444-4444-444444444444', 'Hanh Trinh Vu Tru', 'Mot phi hanh doan tre phai vuot qua vo van hiem nguy trong hanh trinh kham pha he mat troi moi.', 'https://placehold.co/400x600?text=Hanh+Trinh+Vu+Tru', 'PLACEHOLDER_VIDEO_URL', 0, '2026-12-25 00:00:00', '2026-08-01 10:00:00', false, 0, 0, 0, 0, 1, 'd2222222-2222-2222-2222-222222222222', 'JAPAN', 'UPCOMING');

-- Film genres (element collection, no primary key -> guard with NOT EXISTS)
INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f1111111-1111-1111-1111-111111111111' AS film_id, 'SCI_FI' AS genre) t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f1111111-1111-1111-1111-111111111111' AND genre = 'SCI_FI');
INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f1111111-1111-1111-1111-111111111111', 'ACTION') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f1111111-1111-1111-1111-111111111111' AND genre = 'ACTION');

INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f2222222-2222-2222-2222-222222222222', 'ROMANCE') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f2222222-2222-2222-2222-222222222222' AND genre = 'ROMANCE');
INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f2222222-2222-2222-2222-222222222222', 'DRAMA') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f2222222-2222-2222-2222-222222222222' AND genre = 'DRAMA');

INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f3333333-3333-3333-3333-333333333333', 'ACTION') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f3333333-3333-3333-3333-333333333333' AND genre = 'ACTION');
INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f3333333-3333-3333-3333-333333333333', 'THRILLER') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f3333333-3333-3333-3333-333333333333' AND genre = 'THRILLER');

INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f4444444-4444-4444-4444-444444444444', 'SCI_FI') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f4444444-4444-4444-4444-444444444444' AND genre = 'SCI_FI');
INSERT INTO film_genres (film_id, genre)
SELECT * FROM (SELECT 'f4444444-4444-4444-4444-444444444444', 'ANIMATION') t
WHERE NOT EXISTS (SELECT 1 FROM film_genres WHERE film_id = 'f4444444-4444-4444-4444-444444444444' AND genre = 'ANIMATION');

-- Film cast
INSERT IGNORE INTO film_cast (id, film_id, actor_id, character_name, display_order) VALUES
('c1111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 'Dr. Minh', 1),
('c1111111-1111-1111-1111-111111111112', 'f1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222', 'Lan', 2),
('c2222222-2222-2222-2222-222222222221', 'f2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 'Mai', 1),
('c2222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'a3333333-3333-3333-3333-333333333333', 'Nam', 2),
('c3333333-3333-3333-3333-333333333331', 'f3333333-3333-3333-3333-333333333333', 'a1111111-1111-1111-1111-111111111111', 'Captain Long', 1),
('c3333333-3333-3333-3333-333333333332', 'f3333333-3333-3333-3333-333333333333', 'a3333333-3333-3333-3333-333333333333', 'Agent Ha', 2);

-- Episodes (series film only) — video_url is a placeholder string for now
INSERT IGNORE INTO episodes (id, season_number, episode_number, title, video_url, duration_minutes, film_id) VALUES
('e1111111-1111-1111-1111-111111111111', 1, 1, 'Tap 1: Khoi Dau', 'PLACEHOLDER_VIDEO_URL', 42, 'f3333333-3333-3333-3333-333333333333'),
('e2222222-2222-2222-2222-222222222222', 1, 2, 'Tap 2: Truy Duoi', 'PLACEHOLDER_VIDEO_URL', 45, 'f3333333-3333-3333-3333-333333333333'),
('e3333333-3333-3333-3333-333333333333', 1, 3, 'Tap 3: Doi Dau', 'PLACEHOLDER_VIDEO_URL', 47, 'f3333333-3333-3333-3333-333333333333');
