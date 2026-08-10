# Báo cáo rà soát dead code — Frontend (frontend)

Ngày kiểm tra: 2026-08-08
Phạm vi: `src/pages/**`, `src/components/**`
Router: React Router v6, cấu hình duy nhất tại [App.tsx](frontend/src/App.tsx) (đã xác nhận không còn route config nào khác — không dùng `createBrowserRouter`/file-based routing).
KHÔNG có file nào bị xoá trong session này.

## Phương pháp

1. Liệt kê toàn bộ file trong `src/pages/` (29 file) và `src/components/` (21 file).
2. Với từng **page**: đối chiếu tên import với toàn bộ `<Route element={...}>` trong `App.tsx`.
3. Với từng **component**: `grep` toàn bộ chuỗi import (`from '.../TênComponent'`) trên toàn repo frontend, sau đó truy vết bắc cầu — nếu component A chỉ được import bởi component B, kiểm tra tiếp B có được dùng ở nơi "sống" (route hoặc component sống khác) hay không.
4. Áp dụng loại trừ: barrel export `index.ts` re-export, comment WIP/TODO, tên trùng tính năng đang phát triển dở (`git log --follow`, ngưỡng 30 ngày), file `.stories.tsx`.
5. Kiểm tra riêng: không tìm thấy file `*.stories.*` nào trong dự án → loại trừ Storybook không áp dụng. File barrel `index.ts` duy nhất trong 2 thư mục này là `src/pages/Admin/index.tsx`, nhưng đây là page thật (route `/admin`), không phải barrel re-export → không áp dụng loại trừ này.

## Kết quả: nghi ngờ không dùng

| ID | Loại | Đường dẫn file | Lý do nghi ngờ không dùng | Bằng chứng đã kiểm tra | Rủi ro | Trạng thái |
|----|------|----------------|---------------------------|------------------------|--------|------------|
| DC-01 | Page | `src/pages/Film/FilmLatest.tsx` | Không được import ở đâu ngoài chính nó; route cũ `/film/latest` trong `App.tsx:238` chỉ redirect (`<Navigate to="/film/watch-together" replace />`), không render component này. | `grep -rn "FilmLatest"` toàn repo → chỉ khớp trong chính file (khai báo + `displayName` + `export default`). Đối chiếu `App.tsx` → không có `<Route element={<FilmLatest />}>` nào. | Thấp — file độc lập, chỉ phụ thuộc `filmService`, `FilmCard` (đang được dùng ở nơi khác nên không kéo theo dead code phụ). Xoá an toàn về mặt kỹ thuật. | Nghi ngờ không dùng — **chờ xác nhận trước khi xoá** |
| DC-02 | Page | `src/pages/Film/FilmTrending.tsx` | Không được import ở đâu ngoài chính nó; route cũ `/film/trending` trong `App.tsx:237` chỉ redirect (`<Navigate to="/film/search" replace />`), không render component này. | `grep -rn "FilmTrending"` toàn repo → chỉ khớp trong chính file. Đối chiếu `App.tsx` → không có `<Route element={<FilmTrending />}>` nào. | Thấp — cùng lý do DC-01. | Nghi ngờ không dùng — **chờ xác nhận trước khi xoá** |

### Về ngoại lệ "WIP/tính năng đang phát triển dở" — đã xem xét và loại bỏ

Cả hai file trên đều được đụng tới trong commit gần đây (`6ceb104`, 2026-08-07, "feat(frontend): revamp film browsing and admin catalog management" — trong ngưỡng 30 ngày), nên ban đầu có vẻ khớp tiêu chí loại trừ WIP. Tuy nhiên kiểm tra kỹ nội dung commit đó cho thấy đây **không phải WIP** mà là code cũ bị bỏ lại sau khi thay thế:

- Message của chính commit đó ghi rõ: *"...dedicated search page (**replacing trending**)..."*
- Diff của `App.tsx` trong cùng commit cho thấy route `/film/trending` bị đổi thành `<Navigate to="/film/search" replace />` và route `/film/latest` bị đổi thành `<Navigate to="/film/watch-together" replace />` — tức là routing đã cố ý được thay thế bằng trang mới (`FilmSearch`, `FilmWatchTogether`), không phải đang chờ nối route.
- `FilmLatest.tsx` thậm chí được **tạo mới** (75 dòng insert) trong đúng commit đã bỏ route của nó — có thể là code còn sót lại từ một hướng tiếp cận bị đổi ý giữa chừng trong cùng lần commit.
- Không có comment `TODO`/`WIP`/`FIXME` nào trong 2 file.

→ Kết luận: đây là dead code thật sự từ một refactor đã hoàn tất, không phải tính năng đang dang dở. Vẫn giữ nguyên, không xoá — chỉ báo cáo theo đúng yêu cầu.

## Đã kiểm tra — KHÔNG nghi ngờ (để minh bạch, không cần hành động)

### Pages (27/29) — đều có route sống trong `App.tsx`
`SocialHome`, `Login`, `Register`, `ForgotPassword`, `ResetPassword`, `Authenticate`, `Chat`, `Profile`, `Friends`, `Notifications`, `Admin/index` (route `/admin`), `Film/FilmHome`, `Film/FilmSearch`, `Film/FilmWatchTogether`, `Film/WatchRoom`, `Film/FilmLibrary`, `Film/FilmDetail`, `Film/FilmWatch`, `Film/EpisodeUpload`, `Film/FilmCategoryBrowse` (dùng lại 3 route: series/standalone/animation).

Các "tab" trong `Admin/` (`UsersTab`, `RolesTab`, `ActorsTab`, `DirectorsTab`, `FilmsTab`, `EpisodesTab`) đều được import trực tiếp trong `Admin/index.tsx`. `PersonManagementTab` được `ActorsTab` và `DirectorsTab` dùng chung (component cha-con còn sống). `AvatarUploadField` được `PersonManagementTab` và `FilmsTab` dùng chung.

### Components (21/21) — đều truy vết được tới ít nhất 1 route sống
- `Auth/ProtectedRoute` → dùng trực tiếp trong `App.tsx`.
- `Layout/MainLayout` → dùng trực tiếp trong `App.tsx`; nội bộ import `NotificationMenu`, `AccountMenu` (còn sống).
- `Layout/AuthLayout` → dùng bởi `Login`, `Register`, `ForgotPassword`, `ResetPassword`.
- `Film/FilmCard` → dùng bởi `FilmSearch`, `FilmLibrary`, `FilmCategoryBrowse`, `FilmBentoGrid` (các consumer sống — việc `FilmLatest`/`FilmTrending` cũng import không ảnh hưởng vì đã có consumer sống khác).
- `Film/FilmCardFeatured`, `Film/FilmBentoGrid` → chuỗi `FilmShowcaseSection` → `FilmHome` (route `/film`).
- `Film/VideoPlayer` → dùng bởi `FilmWatch`, `WatchRoom`.
- `Comment/CommentComposer`, `Comment/CommentItem` → nội bộ `CommentSection` → dùng bởi `PostCard`, `FilmWatch`, `FilmDetail`.
- `Social/PostCard`, `PostCardImage`, `PostCardWatch`, `PostFeedList` → chuỗi `PostFeed`/`MyPostsFeed` → `SocialHome` (route `/social`).
- `Social/PostComposer` → dùng bởi `PostFeed`.
- `Social/types.ts`, `Social/postComposerOptions.ts` → không phải component (type/util), được `PostCard`, `PostComposer`, `PostFeed`, `PostFeedList`, `MyPostsFeed`, và hook `usePostLikeToggle` import trực tiếp — còn sống.

Không phát hiện cụm "component cha-con cùng chết" nào (mọi component chỉ-được-dùng-bởi-1-file-khác đều truy ngược tới một file đang sống).

## Ghi chú

- Không tìm thấy file `index.ts` barrel export nào cần loại trừ theo tiêu chí #4.
- Không tìm thấy file `.stories.tsx` nào trong dự án.
- File `src/pages/Itinerary.tsx` xuất hiện là đã bị xoá (`D`) trong `git status` hiện tại (chưa commit) — không đưa vào bảng vì không còn tồn tại trên working tree, không thuộc phạm vi "còn tồn tại nhưng không dùng".
- Không có thay đổi/xoá file nào được thực hiện trong session này, đúng theo yêu cầu.
