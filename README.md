# AiREAK Entertainment Platform

Nền tảng giải trí kết hợp **mạng xã hội** (đăng bài, bình luận, kết bạn, chat realtime) và **xem phim trực tuyến** (duyệt/tìm phim, streaming HLS, phòng "xem cùng nhau" đồng bộ realtime), được xây dựng theo kiến trúc **microservices** với Spring Boot ở backend và React SPA ở frontend.

## Tính năng chính (Features)

**Tài khoản & bảo mật**
- Đăng ký / đăng nhập bằng username-password, đăng nhập qua Google OAuth2
- Xác thực bằng JWT (ký chung một secret cho toàn hệ thống), quên mật khẩu / đặt lại mật khẩu qua email
- Phân quyền theo Role (ADMIN/USER)

**Mạng xã hội (Social)**
- Đăng bài, feed bài viết, bình luận theo bài viết
- Kết bạn / quản lý danh sách bạn bè, tìm kiếm người dùng
- Chat nhắn tin realtime (đính kèm file, sửa/xoá/trả lời tin nhắn)
- Thông báo realtime qua WebSocket + gửi email (Brevo)
- Upload avatar/tệp qua presigned URL (Backblaze B2)

**Phim (Film)**
- Duyệt phim theo danh mục (phim bộ, phim lẻ, hoạt hình), tìm kiếm, trang chi tiết phim
- Xem phim streaming (HLS qua `hls.js`), thư viện phim cá nhân
- Phòng "xem cùng nhau" (watch-together) — đồng bộ trạng thái phát giữa nhiều người xem realtime
- Trang quản trị (Admin): quản lý phim, tập phim, diễn viên, đạo diễn, vai trò, người dùng

**Khác**
- Đa ngôn ngữ (i18n) ở frontend
- CDC (Change Data Capture) bằng Debezium/Kafka để đồng bộ dữ liệu giữa các service, có service quản lý connector riêng (`debezium-manager`)

## Công nghệ sử dụng (Tech Stack)

**Backend** — Maven multi-module monorepo
- Java 24, Spring Boot 3.4.5, Spring Cloud 2024.0.1
- API Gateway: Spring Cloud Gateway (reactive/WebFlux) + Spring Security OAuth2 Resource Server (JWT qua Nimbus JOSE)
- Giao tiếp giữa service: OpenFeign, Resilience4j (circuit breaker/retry)
- Dữ liệu: MySQL (JPA/Hibernate), MongoDB, Redis (cache), Elasticsearch (tìm kiếm)
- Message broker: Apache Kafka (KRaft mode) + Debezium (CDC)
- Realtime: Spring WebSocket (chat/socket/room service)
- Lưu trữ file: AWS SDK v2 (S3-compatible client) trỏ tới Backblaze B2
- Lombok, MapStruct, Spotless (format code)
- Test: JUnit 5, Mockito, Spring Boot Test, Testcontainers, H2 (in-memory DB cho test)

**Frontend**
- React 19 + TypeScript, Vite 8
- MUI (Material UI) v9, Tailwind CSS 4, Emotion
- Redux Toolkit + React Redux (state), React Router v7 (routing)
- Axios (HTTP client), i18next (đa ngôn ngữ)
- hls.js (phát video HLS), emoji-picker-react, react-hot-toast
- ESLint + typescript-eslint

**Hạ tầng / DevOps**
- Docker Compose: MySQL, MongoDB (replica set), Kafka, Redis, Elasticsearch, Debezium Connect
- Mỗi service có Dockerfile riêng; frontend build bằng Nginx image
- Maven Wrapper (`mvnw` / `mvnw.cmd`) — không cần cài Maven riêng

## Cấu trúc thư mục (Project Structure)

```
Project/
├── frontend/                   # Frontend React + Vite SPA
├── api-gateway/                # Spring Cloud Gateway — cổng vào duy nhất, xác thực JWT, định tuyến
├── identity-service/           # Auth: đăng ký/đăng nhập, JWT, Google OAuth2, role/user (MySQL)
├── profile-service/            # Hồ sơ người dùng (MongoDB)
├── post-service/               # Bài đăng mạng xã hội (MySQL + MongoDB)
├── comment-service/            # Bình luận (MongoDB)
├── friend-service/             # Kết bạn, tìm kiếm bạn bè (MongoDB + Elasticsearch)
├── chat-service/                # Nhắn tin (MongoDB)
├── socket-service/             # WebSocket gateway cho chat/thông báo realtime
├── notification-service/       # Thông báo + gửi email qua Brevo (MongoDB)
├── file-service/                # Upload/lưu trữ file qua Backblaze B2 (MongoDB metadata)
├── film_service/               # Catalog phim, tập phim, diễn viên/đạo diễn (MySQL + Elasticsearch)
├── room-service/               # Phòng xem phim cùng nhau, đồng bộ realtime (MongoDB)
├── debezium-manager/           # Quản lý Kafka Debezium connector (CDC)
├── common/                     # Thư viện dùng chung (DTO, exception, util...)
├── common-redis/               # Cấu hình Redis dùng chung
├── common-security/            # Cấu hình bảo mật/JWT dùng chung
├── common-elasticsearch/       # Cấu hình Elasticsearch dùng chung
├── infrastructure/db-init/     # Script khởi tạo MySQL / MongoDB replica set
├── docker-compose.yml          # Orchestrate toàn bộ hạ tầng + services
├── pom.xml                     # Maven parent (multi-module)
└── .env.example                # Mẫu biến môi trường
```

## Hướng dẫn cài đặt (Installation)

### Yêu cầu
- Docker & Docker Compose (cách chạy khuyến nghị — tự dựng toàn bộ hạ tầng)
- Để chạy/dev thủ công từng service: JDK 24, Node.js ≥ 22 (dùng cho `npm ci` trong Dockerfile frontend)

### Chạy bằng Docker Compose (khuyến nghị)

```bash
# 1. Clone project
git clone <repository-url>
cd Project

# 2. Tạo file .env từ mẫu và điền giá trị thật (không commit .env)
cp .env.example .env

# 3. Build & chạy toàn bộ hệ thống (infra + tất cả microservices + frontend)
docker compose up -d --build
```

> Máy cấu hình yếu nên build/khởi động từng service một thay vì chạy đồng loạt, ví dụ:
> `docker compose build identity && docker compose up -d --no-deps identity`

### Chạy backend thủ công (không qua Docker)

```bash
# Build toàn bộ module (dùng Maven Wrapper, không cần cài Maven)
./mvnw clean install        # Windows: mvnw.cmd clean install

# Chạy một service cụ thể, ví dụ identity-service
./mvnw -pl identity-service spring-boot:run
```
Cần có sẵn MySQL/MongoDB/Redis/Kafka/Elasticsearch đang chạy (có thể chỉ `docker compose up` các service hạ tầng) và các biến môi trường tương ứng trong `.env`/biến hệ thống.

### Chạy frontend thủ công (dev mode)

```bash
cd frontend
npm install
npm run dev
```
Mặc định Vite chạy ở `http://localhost:5173` (cần đúng cổng này để khớp CORS allowlist của backend).

## Hướng dẫn sử dụng (Usage)

Sau khi `docker compose up -d --build` thành công, các endpoint chính:

| Service | URL (docker) | Ghi chú |
|---|---|---|
| Frontend (web-app) | http://localhost:5173 | Nginx phục vụ bản build |
| API Gateway | http://localhost:8888 | Điểm vào API duy nhất cho frontend |
| identity-service | http://localhost:8080 | Auth |
| profile-service | http://localhost:8081 | Hồ sơ |
| notification-service | http://localhost:8082 | Thông báo/email |
| post-service | http://localhost:8083 | Bài đăng |
| file-service | http://localhost:8084 | File/upload |
| chat-service | http://localhost:8086 | Chat |
| friend-service | http://localhost:8087 | Bạn bè |
| socket-service | http://localhost:8088 (WS) | Realtime chat/thông báo |
| comment-service | http://localhost:8089 | Bình luận |
| film-service | http://localhost:8090 | Catalog phim |
| room-service | http://localhost:8091 | Xem phim cùng nhau |
| debezium-manager | http://localhost:9000 | Quản lý CDC connector |
| Debezium Connect | http://localhost:8100 | Kafka Connect REST |

`identity-service` tự động seed một tài khoản admin khi khởi động lần đầu (nếu chưa tồn tại): **username `admin` / password `admin`** — dùng để đăng nhập thử/quản trị ở môi trường dev (xem [ApplicationInitConfig.java](identity-service/src/main/java/com/MyProject/identity/identity_service/configuration/ApplicationInitConfig.java)).

Đăng nhập Google OAuth2 yêu cầu cấu hình `CLIENT_ID`/`CLIENT_SECRET` thật trong `.env`, nếu không sẽ không hoạt động.

## Biến môi trường / Cấu hình

Toàn bộ biến môi trường được khai báo tập trung trong **một file `.env` duy nhất ở thư mục gốc** (mẫu tại [.env.example](.env.example)), được các service Docker Compose dùng chung qua `env_file`. Các nhóm chính:

- **Hạ tầng**: image & tên container cho MySQL, MongoDB, Kafka, Redis, Elasticsearch; thông tin kết nối (host/port/user/password)
- **Bảo mật**: `JWT_SIGNER_KEY` (chung cho mọi service), cấu hình Google OAuth2 (`CLIENT_ID`, `CLIENT_SECRET`, `REDIRECT_URI`), thời hạn access/refresh token
- **URL nội bộ giữa các service**: dùng tên service Docker khi chạy container (`http://identity:8080`), dùng `localhost` khi chạy local dev
- **Email** (notification-service): API key Brevo, tên/email người gửi
- **Backblaze B2** (file-service): key, bucket, endpoint/region, giới hạn dung lượng file theo loại (ảnh/video/raw)
- **Khác**: timezone (`TZ`), bật/tắt rate limit công khai

**Không** commit file `.env` thật (đã có trong [.gitignore](.gitignore)).

## Cách chạy test

**Backend** (JUnit 5 + Mockito + Spring Boot Test; một số module dùng Testcontainers/H2):

```bash
# Chạy test cho toàn bộ module
./mvnw test

# Chạy test cho một service cụ thể
./mvnw -pl film_service test
```
Các module hiện có test: `identity-service`, `profile-service`, `post-service`, `comment-service`, `file-service`, `chat-service`, `socket-service`, `friend-service`, `film_service`, `notification-service`, `room-service`, `api-gateway` (nhiều nhất ở `film_service`). `debezium-manager` chưa có test.

**Frontend**: hiện chưa cấu hình bộ test tự động (không có script `test` trong `package.json`), chỉ có lint:
```bash
cd frontend
npm run lint
```

## License

Không tìm thấy file `LICENSE` trong repository. [Điền loại giấy phép ở đây nếu có]
