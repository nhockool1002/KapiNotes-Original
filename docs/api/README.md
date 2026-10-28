# KapiNotes Backend API (tùy chọn - hiện KHÔNG được dùng)

> **Cập nhật:** App hiện đăng nhập bằng Google và đồng bộ ghi chú trực tiếp lên Google Drive của
> chính người dùng (`DriveSyncAuthRepository`) - xem [README.md](../../README.md) mục 5. Tài
> liệu backend dưới đây **không còn bắt buộc** cho use-case học tập cá nhân hiện tại; nó chỉ còn
> giá trị tham khảo nếu sau này thực sự cần một server riêng, ví dụ để chia sẻ ghi chú giữa
> nhiều tài khoản Google khác nhau (điều mà lưu trên Drive cá nhân của từng người không làm
> được).

Đây là tài liệu đặc tả một backend KapiNotes **giả định**, cho trường hợp dự án lớn lên và cần
chia sẻ dữ liệu giữa nhiều người dùng. Tài liệu này mô tả API cần dựng để thay thế
`DriveSyncAuthRepository` mà **không cần đổi UI**, chỉ cần viết một implementation khác cho các
interface `AuthRepository` / `NotesRepository` (xem `di/AppModule.kt`).

File đặc tả chi tiết: [`openapi.yaml`](./openapi.yaml) (OpenAPI 3.0, có thể import vào Postman,
Swagger UI, hoặc dùng để generate server/client code).

## 1. Mô hình dữ liệu

```
User 1---* Note 1---* Page 1---* Stroke
```

- **User**: tài khoản KapiNotes (email/password hoặc liên kết qua Google Sign-In mà app đã xin
  quyền Drive sẵn).
- **Note**: một ghi chú. `source = BLANK` (tạo trắng) hoặc `DRIVE_IMPORT` (tạo từ file Drive).
  Có `isFavorite`, `isTrashed` (xoá mềm, giống "Thùng rác" trên Dashboard).
- **Page**: một trang trong ghi chú. Có `paperType` (BLANK/LINED/GRID/DOT),
  `orientation` (PORTRAIT/LANDSCAPE - nút "Xoay"), `flip` (NONE/HORIZONTAL - nút "Lật"), và
  `backgroundFileId` khi trang này là ảnh trang PDF/ảnh đã import từ Drive.
- **Stroke**: một nét bút/nét nhớ đã hoàn thành. Lưu toàn bộ điểm lấy mẫu (x, y, pressure) để
  client vẽ lại chính xác và để export ra PDF phía server nếu cần. Lưu ý: **cục tẩy không sửa
  nét vẽ** - nó xoá toàn bộ stroke giao với đường tẩy, nên API chỉ cần `POST` (thêm) và
  `DELETE` (xoá) theo từng stroke, không có "sửa điểm".

## 2. Xác thực

- `POST /auth/register`, `POST /auth/login`: email/password cổ điển.
- `POST /auth/login/google`: dùng lại chính Google Sign-In mà app đã bật để kết nối Drive
  (scope `drive.readonly` + `drive.file`), gửi `idToken` lên để đổi thành session KapiNotes -
  người dùng không cần đăng nhập 2 lần.
- Access token JWT ngắn hạn (gợi ý 15-30 phút) + refresh token dài hạn, `POST /auth/refresh` để
  lấy cặp token mới. Mọi endpoint còn lại dùng `Authorization: Bearer <accessToken>`.

## 3. Đồng bộ (Sync)

App hiện luôn hoạt động **offline-first**: mọi thay đổi ghi vào Room ngay lập tức, không chờ
mạng. Khi có backend thật:

1. `GET /sync/pull?since=<timestamp>` - lấy mọi thay đổi (note/page/stroke mới hoặc bị xoá) kể
   từ lần đồng bộ trước.
2. `POST /sync/push` - gửi thay đổi cục bộ lên, kèm `updatedAt` của từng record.
3. Server dùng **last-write-wins theo `updatedAt`**; nếu bản trên server mới hơn bản client gửi
   lên, server trả về record đó trong `conflictingNoteIds` và client phải `pull` lại record đó
   thay vì ghi đè.
4. Riêng **strokes không conflict theo nội dung** (chỉ có thêm/xoá nguyên stroke), nên phần dễ
   xung đột nhất là `Note.title` và `Page` layout - cũng là lý do schema tách nhỏ theo
   note/page/stroke thay vì một blob JSON lớn.

Client nên debounce: gom nhiều `Stroke` lại rồi gọi `POST /pages/{id}/strokes` theo lô (ví dụ
mỗi 2-3 giây hoặc khi rời trang), thay vì gọi API theo từng nét vẽ.

## 4. Nhập tài liệu từ Google Drive

App tự lo việc gọi Google Drive REST API (`files.list`, `files.get` để tải file) và tự
rasterize PDF/ảnh thành bitmap từng trang (bằng `android.graphics.pdf.PdfRenderer`) - xem
`drive/DriveRepository.kt` và `pdf/DocumentPageRenderer.kt`. Backend **không cần gọi Drive
API**, chỉ cần:

1. Nhận từng ảnh trang đã render qua `POST /files` (multipart).
2. Gọi `POST /notes/import-drive` với `driveFileId`, `driveFileName` và danh sách
   `pageFileIds` để tạo Note + Page tương ứng.

Việc ghi ngược lại Drive (ví dụ lưu file PDF đã export vào đúng thư mục Drive) có thể làm hoàn
toàn trên client bằng `Drive.files().create(...)` (đã có sẵn trong `DriveRepository.uploadFile`)
mà không cần qua backend.

## 5. Export & Backup

- **Export PDF** (nút "Xuất file" trong Editor) hiện làm 100% trên máy
  (`pdf/NoteExporter.kt`, dùng `android.graphics.pdf.PdfDocument`) rồi mở Share sheet - không
  cần endpoint riêng.
- **Backup toàn bộ dữ liệu** (Settings > "Xuất dữ liệu"/"Nhập dữ liệu"/"Xóa tất cả") hiện cũng
  100% cục bộ: `data/repository/BackupRepository.kt` đóng gói Room + ảnh nền trang vào một file
  zip (`manifest.json` + `assets/`). `GET /backup/export` ở API thật trả về **đúng cấu trúc
  zip này** ở mức tài khoản, để một bản backup có thể dùng lẫn giữa "export ra file" và
  "đồng bộ lên server".

## 6. Gợi ý triển khai

- Bất kỳ stack nào trả đúng JSON theo `openapi.yaml` đều dùng được (Node/Express,
  Kotlin/Ktor, Go, Django REST...). Vì toàn bộ app Android gọi qua các interface
  `AuthRepository`/`NotesRepository`, chỉ cần viết thêm `RemoteAuthRepository`,
  `RemoteNotesRepository` implement các interface đó bằng Retrofit (đã có sẵn dependency
  Retrofit + kotlinx-serialization converter trong `app/build.gradle.kts`) và đổi binding trong
  `di/AppModule.kt`.
- Lưu `colorArgb` dạng số nguyên 64-bit (packed ARGB) như client đang dùng, tránh phải convert
  qua chuỗi hex ở backend.
- `widthPx`/`heightPx` của Page nên lưu đúng độ phân giải client đã render (mặc định app dùng
  1588x2246, tỉ lệ A4 ở DPI cao để nét bút không bị mờ khi export).
