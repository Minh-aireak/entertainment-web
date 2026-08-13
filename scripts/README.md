# Scripts

Thư mục này chứa công cụ vận hành local và bảo trì dữ liệu. Ứng dụng không nạp hay thực thi trực tiếp các file trong đây lúc runtime.

| File | Mục đích | Side effect chính |
|---|---|---|
| `import-local-env.ps1` | Nạp `.env` vào PowerShell hiện tại và đổi hostname Docker thành `localhost` để chạy Spring Boot từ host. | Chỉ thay đổi biến môi trường của process PowerShell hiện tại. Phải dot-source: `. .\scripts\import-local-env.ps1`. |
| `start-compose-sequential.ps1` | Build và khởi động hạ tầng, service, gateway và frontend theo thứ tự để giảm tải CPU/RAM. | Chủ đích khởi động toàn bộ stack; có thể dùng `-SkipBuild` để không build lại image ứng dụng. |
| `rotate-local-secrets.ps1` | Sinh credential local mạnh, cập nhật `.env` và đồng bộ tài khoản đã lưu trong volume MySQL/MongoDB. | Có thể tạm khởi động/dừng `mysql` và `mongodb`; tạo `.env.rotation-pending` để phục hồi khi bị gián đoạn. Không in secret ra terminal. |
| `film-seed-normalize.sql` | Chuẩn hóa một database làm việc tạm trước khi chụp snapshot seed phim. | Có `DELETE`, `UPDATE` và ID hard-code; không chạy trực tiếp trên dữ liệu cần giữ hoặc production. |
| `build-film-data-sql.ps1` | Chuyển các câu `INSERT` từ dump đã chuẩn hóa thành seed idempotent và tạo sự kiện `film.sync`. | Ghi đè file được truyền qua `-OutputPath`; chỉ hỗ trợ định dạng dump mà script kiểm tra. |

## Cách dùng thường gặp

```powershell
# Chạy một backend trực tiếp từ host
. .\scripts\import-local-env.ps1
.\mvnw.cmd -pl identity-service spring-boot:run

# Khởi động tuần tự toàn bộ Docker Compose
.\scripts\start-compose-sequential.ps1

# Rebuild riêng một service khi dependency đã chạy
docker compose up -d --build --no-deps room
```

Hai file seed là công cụ bảo trì có chủ đích, không phải migration tự động. Hãy đọc nội dung, dùng database tạm và tạo backup trước khi chạy.

Các container hạ tầng trong `docker-compose.yml` dùng `restart: "no"`; mở Docker Desktop không tự làm chúng chạy. Riêng script `start-compose-sequential.ps1` là lệnh start chủ động nên sẽ dựng hạ tầng. Khi rebuild một service đã có đủ dependency, luôn dùng `docker compose up -d --build --no-deps <service>`.

## Chính sách Git

Nên commit các script tái lập được quy trình build/chạy/seed, file README này, cùng sơ đồ và ảnh tài liệu trong `docs/`. Các file đang có trong `scripts/` đều là nguồn vận hành/bảo trì có thể tái sử dụng, không phải output runtime.

Danh sách cụ thể cần phân biệt trong repository hiện tại:

- `/.env`: credential local thật; đang bị Git ignore, không commit.
- `/.env.rotation-pending`: hiện không tồn tại; chỉ được `rotate-local-secrets.ps1` tạo làm checkpoint phục hồi rồi xóa khi xoay secret thành công. Mẫu `.env.*` trong `.gitignore` đã chặn file này.
- Hiện không có database dump hoặc file credential dạng `*.dump`, `*.backup`, `*.sql.gz`, `credentials.json`, `service-account*.json`, `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks` trong cây dự án.
- `identity-service/src/main/resources/data.sql` và `film_service/src/main/resources/data.sql` là seed được version hóa; hai file trong `film_service/src/main/resources/sql/` là migration; `scripts/film-seed-normalize.sql` là script bảo trì. Ba nhóm này không phải database dump.
- File xuất tạm là output một lần như `*.export.sql`, `*.export.json`, `*.export.csv`, `*.tmp`, hoặc nội dung trong `dumps/`, `backups/`, `exports/`, `tmp/`; hiện repository không có file nào thuộc nhóm này.

Nếu một script chỉ phục vụ một lần và không còn tái tạo được quy trình hiện tại, nên xóa hoặc chuyển vào tài liệu lịch sử thay vì để lẫn với công cụ vận hành.
