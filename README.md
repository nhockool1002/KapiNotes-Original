# KapiNotes

Ứng dụng ghi chú viết tay (kiểu KiloNotes) cho Android, tối ưu cho bút cảm ứng (stylus) trên
**Xiaomi Pad Pro**. Giao diện bám theo wireframe `kilonotes_wireframe.html`: Dashboard (sidebar +
lưới tài liệu) và Editor (thanh công cụ trên cùng + thanh màu/độ dày + vùng giấy).

Đã build và chạy thử thật trên Android emulator (và cài được lên thiết bị thật) bằng
`gradle :app:assembleDebug` - xem mục 3 nếu bạn muốn tự build bằng Android Studio.

## 1. Những gì đã có

| Khu vực | Trạng thái |
|---|---|
| Dashboard (sidebar, tìm kiếm, lưới ghi chú, yêu thích, thùng rác) | Hoạt động thật, dữ liệu từ Room |
| Tạo ghi chú trắng + chọn loại giấy (trắng/kẻ ngang/ô vuông/chấm) | Hoạt động thật |
| Nhập PDF/ảnh làm nền ghi chú (qua System File Picker của Android - gồm cả Google Drive, Downloads, bộ nhớ máy...) | Hoạt động thật |
| Bút vẽ: Pen (nhận áp lực bút cảm ứng), Highlighter, Eraser, chọn màu, độ dày | Hoạt động thật |
| Lasso: khoanh vùng rồi kéo để di chuyển ("cắt") các nét vẽ sang chỗ khác | Hoạt động thật |
| Undo/Redo theo từng cử chỉ (vẽ/tẩy/di chuyển = 1 bước) | Hoạt động thật |
| Xoay trang, lật trang | Hoạt động thật |
| Xuất ghi chú ra PDF (gộp nền + toàn bộ nét vẽ) rồi mở Share sheet | Hoạt động thật |
| Xuất/Nhập/Xóa toàn bộ dữ liệu cục bộ (file `.kapibackup`) | Hoạt động thật |
| Đăng nhập Google + đồng bộ ghi chú đa thiết bị qua sync server riêng | Hoạt động thật - xem mục 5 |
| Kiểm tra bản cập nhật trên Google Play (Play In-App Updates) - chỉ hoạt động khi cài qua Play Store | Hoạt động thật |
| Chia sẻ ghi chú dạng link xem-only (upload PDF lên Drive của chính người dùng) | Hoạt động thật - xem mục 4 |
| Công cụ chèn Text / chèn Image độc lập (khác với nền trang) | Chưa làm |

## 2. Kiến trúc

```
app/src/main/java/com/kapi/notes/
├── domain/model/        Model nghiệp vụ thuần Kotlin (Note, NotePage, Stroke, UserSession, ...)
├── data/
│   ├── local/            Room (NoteEntity/PageEntity/StrokeEntity + DAO) - nguồn sự thật cục bộ
│   ├── datastore/        DataStore lưu session (tài khoản Google đang đăng nhập)
│   └── repository/       NotesRepository, AuthRepository (interface) + DriveSyncAuthRepository,
│                         BackupRepository (đóng gói/mở file .kapibackup dùng chung cho cả
│                         export/import thủ công lẫn đồng bộ qua sync server)
├── drive/                DriveAuthManager (Google Sign-In: ID token + scope drive.file) +
│                         DriveRepository (chỉ còn upload + chia sẻ link view-only - KHÔNG còn
│                         duyệt/liệt kê file, xem mục 4)
├── sync/                 SyncApi (gọi Cloudflare Worker trong sync-worker/ để đồng bộ backup) +
│                         SyncLogger
├── pdf/                  DocumentPageRenderer (PDF/ảnh -> bitmap trang), NoteExporter (-> PDF)
├── update/               UpdateChecker (GitHub releases - hiện TẮT, xem GITHUB_UPDATE_CHECK_ENABLED
│                         trong UpdateChecker.kt, vì giờ chỉ phát hành qua Play Store) +
│                         PlayUpdateChecker/PlayUpdateViewModel (Play In-App Updates)
├── di/                   Hilt modules
└── ui/
    ├── dashboard/        Màn hình chính
    ├── editor/            Màn hình vẽ (toolbar, DrawingCanvas, Lasso, undo/redo, engine vẽ)
    ├── settings/          Đăng nhập Google + đồng bộ + quản lý dữ liệu cục bộ
    └── theme/, navigation/, common/

sync-worker/               Cloudflare Worker + R2 bucket riêng cho đồng bộ (xem mục 5) - tách khỏi
                           app module, deploy bằng `wrangler deploy` trong thư mục này.
```

## 3. Build / chạy thử

```bash
# Biên dịch
gradle :app:compileDebugKotlin

# Build APK debug đầy đủ
gradle :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Yêu cầu: JDK 17 (project dùng AGP 8.5.2, chưa tương thích JDK mới hơn - nếu máy bạn mặc định
JDK khác, set `JAVA_HOME` trỏ tới JDK 17 trước khi chạy lệnh trên), Android SDK với
`platform-tools`, `platforms;android-35`, `build-tools;35.0.0` đã cài qua `sdkmanager`, và file
`local.properties` ở gốc project với dòng `sdk.dir=<đường dẫn SDK>`.

Mở bằng Android Studio (Koala/Ladybug trở lên) cũng chạy được bình thường - IDE sẽ tự
sync Gradle khi mở thư mục này.

## 4. Cấu hình Google Sign-In (OAuth)

App **không xin bất kỳ quyền Google API/Drive nào cả** - đăng nhập chỉ lấy định danh (email) + 1
ID token (`auth/GoogleAuthManager.kt`), dùng để xác thực với Worker riêng của app (mục 5), cả cho
đồng bộ ghi chú lẫn tính năng "Chia sẻ link xem-only" (giờ cũng upload lên R2 qua Worker, không
còn upload lên Drive của người dùng nữa). Vì không xin scope nhạy cảm/hạn chế nào, không bị giới
hạn 100 test user và không cần đánh giá bảo mật CASA khi publish rộng - cũng không cần bật
**Google Drive API** trong Cloud Console nữa.

1. Vào [Google Cloud Console](https://console.cloud.google.com/) → tạo project.
2. **Google Auth Platform → Audience** → mục **Test users**, thêm (các) tài khoản Gmail sẽ dùng để
   test (bắt buộc khi app còn ở trạng thái "Testing" - tối đa 100 người, xem mục 5 nếu cần mở rộng
   hơn).
3. **Clients** → **Create client** → loại **Android** - debug và release build có package name
   KHÁC NHAU (`applicationIdSuffix` trong `app/build.gradle.kts`), nên **mỗi build type cần một
   client riêng**:
   - `com.kapi.notes.debug` (debug build) - SHA-1 lấy bằng `gradle :app:signingReport`.
   - `com.kapi.notes` (release build - **người dùng chỉ cài/cập nhật qua Google Play Store**;
     GitHub Releases vẫn được tạo để lưu trữ file APK/AAB, nhưng không phải nơi người dùng tải
     app) - SHA-1 phải là chứng chỉ **Play App Signing** (Play Console → app → Protected with
     Play → Play Store protection → Manage Play app signing → "SHA-1 certificate fingerprint"
     dưới mục "App signing key"), KHÔNG PHẢI SHA-1 của keystore local trong `keystore/` - Play tự
     ký lại AAB bằng chứng chỉ riêng của Play trước khi cài lên máy người dùng, nên local
     keystore's SHA-1 không khớp với bản thực tế chạy trên máy.
4. Tạo thêm **1 client loại Web application** (không cần redirect URI) - Client ID của client này
   dùng làm "audience" để Worker xác thực ID token (xem mục 5), KHÔNG phải để đăng nhập trên web.
   Đặt Client ID này vào `GOOGLE_SERVER_CLIENT_ID` trong `app/build.gradle.kts` và vào
   `ALLOWED_AUDIENCES` trong `sync-worker/wrangler.toml`.
5. Không cần file `google-services.json` (app không dùng Firebase). Client loại "Android" được
   Play Services tự nhận qua package name + SHA-1 lúc runtime - không cần nhúng Client ID của nó
   vào code.

Build với keystore khác (không phải debug.keystore mặc định) cần tạo thêm OAuth Client ID Android
tương ứng với SHA-1 của keystore đó.

## 5. Đăng nhập, đồng bộ & chia sẻ link - Cloudflare Worker + R2, không cần tự vận hành server

App không tự host backend, và không gọi bất kỳ Google API nào ngoài Sign-In. Mọi lưu trữ (đồng bộ
ghi chú + file PDF chia sẻ) đều qua **một Worker riêng của app**:

- **Đăng nhập** = Google Sign-In chỉ lấy **ID token** (`auth/GoogleAuthManager.kt`) - không có
  tài khoản/mật khẩu riêng của KapiNotes, và vì không xin scope nào cả nên không bị giới hạn 100
  test user / không cần CASA khi publish rộng.
- **Đồng bộ** = đóng gói toàn bộ Room (notes/pages/strokes) + ảnh nền trang thành một file zip
  bằng `BackupRepository` (định dạng giống hệt file `.kapibackup` của nút "Xuất dữ liệu" thủ công
  trong Cài đặt), rồi gửi file đó (qua `SyncApi`, kèm ID token) lên **Worker riêng của app** trong
  [`sync-worker/`](sync-worker/) - Worker verify ID token rồi đọc/ghi file vào R2, dưới khoá
  `users/<google-sub>/kapinotes_sync.kapibackup`. App không bao giờ cầm credential của R2.
- **Chia sẻ link xem-only** (Editor) = export PDF rồi `POST /share` (có kèm ID token) lên cùng
  Worker - Worker lưu vào R2 dưới một UUID ngẫu nhiên, trả về id đó. App tự ghép thành URL dạng
  `<SYNC_BASE_URL>/shared/<uuid>` để gửi đi. `GET /shared/<uuid>` **không cần đăng nhập** (đúng
  kiểu "ai có link cũng xem được"), vì UUID ngẫu nhiên trong đường dẫn chính là thứ chặn truy cập,
  không phải phiên đăng nhập của người nhận link. Mỗi lần chia sẻ tạo một object mới, không ghi
  đè - gửi nhiều note khác nhau không làm hỏng link cũ đã gửi trước đó.
- **Mở trên thiết bị khác**: đăng nhập cùng tài khoản Google → app tải file backup của tài khoản
  đó về từ Worker và merge vào Room cục bộ (upsert theo id, nên ghi chú có sẵn trên máy mới không
  bị mất) → rồi đẩy lại bản đã merge lên.
- Nút **"Đồng bộ ngay"** trong Cài đặt làm thủ công cả hai chiều bất cứ lúc nào. Hợp nhất theo
  kiểu "upsert toàn bộ" đơn giản, không giải quyết xung đột phức tạp theo từng trường - đủ dùng
  cho ghi chú cá nhân/học tập, không nhằm cho nhiều người sửa cùng lúc.
- **Triển khai Worker**: xem [`sync-worker/wrangler.toml`](sync-worker/wrangler.toml) - cần một
  tài khoản Cloudflare (free tier đủ dùng), một R2 bucket, và `wrangler deploy` trong thư mục đó.
  Vì dữ liệu giờ nằm trong R2 của bạn (không còn trong Drive riêng từng người dùng), bạn là người
  chịu trách nhiệm lưu trữ/bảo mật/xoá dữ liệu khi người dùng yêu cầu (kể cả các file đã chia sẻ
  qua `/share` - hiện chưa có cơ chế tự xoá/hết hạn, xem mục khuyến nghị dài hạn bên dưới) - xem
  lại `docs/play-store-listing-en-vi.md` mục 5 (Privacy Policy) trước khi phát hành.

Thư mục [`docs/api/`](docs/api/) (OpenAPI + mô tả backend) vẫn còn trong repo làm tài liệu tham
khảo cho một backend đầy đủ hơn nếu sau này thực sự cần (ví dụ chia sẻ ghi chú giữa nhiều người
dùng khác tài khoản Google), nhưng **không bắt buộc và không được dùng** trong app hiện tại.

## 6. Giới hạn / việc tiếp theo nên làm

- Chưa có test (unit/UI) - nên thêm test cho `UndoRedoStack`, `strokeIntersects`/`pointInPolygon`
  (logic tẩy và Lasso), và `BackupRepository` trước khi dùng ở production.
- Đồng bộ hiện chỉ merge kiểu "upsert toàn bộ", không phát hiện xung đột khi cùng một ghi chú bị
  sửa khác nhau trên 2 thiết bị trước khi đồng bộ (thiết bị đồng bộ sau sẽ ghi đè) - chấp nhận
  được cho use-case cá nhân nhưng nên cải thiện nếu mở rộng.
- Chèn văn bản (Text) / chèn ảnh rời (Image) như một đối tượng trên trang (khác với ảnh nền
  trang) chưa làm.
- Component `PaperBackground`/`NoteExporter` vẽ pattern giấy bằng code (không dùng ảnh tĩnh) để
  nhẹ và dễ đổi màu theo theme sáng/tối.
