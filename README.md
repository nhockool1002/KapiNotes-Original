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
| Kết nối Google Drive, duyệt thư mục, nhập PDF/ảnh làm nền ghi chú | Hoạt động thật (cần cấu hình OAuth - xem mục 4) |
| Nhập PDF/ảnh **từ bộ nhớ máy** (không qua Drive) làm nền ghi chú | Hoạt động thật |
| Bút vẽ: Pen (nhận áp lực bút cảm ứng), Highlighter, Eraser, chọn màu, độ dày | Hoạt động thật |
| Lasso: khoanh vùng rồi kéo để di chuyển ("cắt") các nét vẽ sang chỗ khác | Hoạt động thật |
| Undo/Redo theo từng cử chỉ (vẽ/tẩy/di chuyển = 1 bước) | Hoạt động thật |
| Xoay trang, lật trang | Hoạt động thật |
| Xuất ghi chú ra PDF (gộp nền + toàn bộ nét vẽ) rồi mở Share sheet | Hoạt động thật |
| Xuất/Nhập/Xóa toàn bộ dữ liệu cục bộ (file `.kapibackup`) | Hoạt động thật |
| Đăng nhập Google + đồng bộ ghi chú qua Google Drive của người dùng | Hoạt động thật - xem mục 5 |
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
│                         export/import thủ công lẫn đồng bộ Drive)
├── drive/                DriveAuthManager (Google Sign-In) + DriveRepository (Drive REST API:
│                         liệt kê/tải file, và tạo/cập nhật file backup đồng bộ)
├── pdf/                  DocumentPageRenderer (PDF/ảnh -> bitmap trang), NoteExporter (-> PDF)
├── di/                   Hilt modules
└── ui/
    ├── dashboard/        Màn hình chính
    ├── drive/            Màn hình chọn file Drive
    ├── editor/            Màn hình vẽ (toolbar, DrawingCanvas, Lasso, undo/redo, engine vẽ)
    ├── settings/          Đăng nhập Google + đồng bộ + quản lý dữ liệu cục bộ
    └── theme/, navigation/, common/
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

## 4. Cấu hình Google Drive (OAuth)

App dùng chung **một** lượt đăng nhập Google cho cả hai việc: chọn file từ Drive để ghi chú lên,
và đăng nhập tài khoản để đồng bộ ghi chú (mục 5). Cần đăng ký app với Google trước:

1. Vào [Google Cloud Console](https://console.cloud.google.com/) → tạo project → bật **Google
   Drive API**.
2. **OAuth consent screen** (External) → thêm scope `.../auth/drive.readonly` +
   `.../auth/drive.file` → mục **Test users**, thêm (các) tài khoản Gmail sẽ dùng để test (bắt
   buộc khi app còn ở trạng thái "Testing").
3. **Credentials** → **Create Credentials** → **OAuth client ID** → loại **Android**:
   - Package name: `com.kapi.notes` (release) hoặc `com.kapi.notes.debug` (debug build, có hậu
     tố `.debug` do `applicationIdSuffix` trong `app/build.gradle.kts`)
   - SHA-1: lấy bằng `gradle :app:signingReport` (xem dòng `Variant: debug`)
4. Không cần file `google-services.json` (app không dùng Firebase) và **không cần nhúng Client
   ID vào code/app** - loại client "Android" được Play Services tự nhận qua package name + SHA-1
   lúc runtime.
5. Chờ vài phút đến vài giờ để cấu hình lan truyền, rồi mở app → Dashboard → "Google Drive" hoặc
   Cài đặt → "Đăng nhập bằng Google".

Build debug mới (keystore khác) hoặc build release cần tạo thêm OAuth Client ID tương ứng với
SHA-1 của keystore đó.

## 5. Đăng nhập & đồng bộ - không có backend riêng

Theo đúng yêu cầu "ghi chú cho mục đích học tập, không cần bảo mật phức tạp", app **không có
server/API riêng**. `DriveSyncAuthRepository` (implement interface `AuthRepository`) làm toàn bộ
việc "đăng nhập + đồng bộ" chỉ bằng Google Sign-In + Google Drive của chính người dùng:

- **Đăng nhập** = đăng nhập Google (dùng lại `DriveAuthManager` đã có sẵn cho tính năng chọn file
  Drive) - không có tài khoản/mật khẩu riêng của KapiNotes.
- **Đồng bộ** = đóng gói toàn bộ Room (notes/pages/strokes) + ảnh nền trang thành một file zip
  bằng `BackupRepository` (định dạng giống hệt file `.kapibackup` của nút "Xuất dữ liệu" thủ
  công trong Cài đặt), rồi lưu file đó vào Drive của chính tài khoản đang đăng nhập, dưới tên cố
  định `kapinotes_sync.kapibackup` (dùng scope `drive.file` nên chỉ app này thấy/sửa được file
  đó, không đụng tới các file khác của người dùng).
- **Mở trên thiết bị khác**: đăng nhập cùng tài khoản Google → app tự tìm, tải file
  `kapinotes_sync.kapibackup` về và merge vào Room cục bộ (upsert theo id, nên ghi chú có sẵn
  trên máy mới không bị mất) → rồi đẩy lại bản đã merge lên Drive.
- Nút **"Đồng bộ ngay"** trong Cài đặt làm thủ công cả hai chiều (kéo về rồi đẩy lên) bất cứ lúc
  nào. Hợp nhất theo kiểu "upsert toàn bộ" đơn giản, không có giải quyết xung đột phức tạp theo
  từng trường - đủ dùng cho ghi chú cá nhân/học tập, không nhằm cho nhiều người sửa cùng lúc.

Thư mục [`docs/api/`](docs/api/) (OpenAPI + mô tả backend) vẫn còn trong repo làm tài liệu tham
khảo nếu sau này thực sự cần một backend riêng (ví dụ muốn chia sẻ ghi chú giữa nhiều người dùng
khác tài khoản Google), nhưng **không bắt buộc và không được dùng** trong app hiện tại.

## 6. Giới hạn / việc tiếp theo nên làm

- Chưa có test (unit/UI) - nên thêm test cho `UndoRedoStack`, `strokeIntersects`/`pointInPolygon`
  (logic tẩy và Lasso), và `BackupRepository` trước khi dùng ở production.
- Đồng bộ Drive hiện chỉ merge kiểu "upsert toàn bộ", không phát hiện xung đột khi cùng một ghi
  chú bị sửa khác nhau trên 2 thiết bị trước khi đồng bộ (thiết bị đồng bộ sau sẽ ghi đè) - chấp
  nhận được cho use-case cá nhân nhưng nên cải thiện nếu mở rộng.
- Chèn văn bản (Text) / chèn ảnh rời (Image) như một đối tượng trên trang (khác với ảnh nền
  trang) chưa làm.
- Component `PaperBackground`/`NoteExporter` vẽ pattern giấy bằng code (không dùng ảnh tĩnh) để
  nhẹ và dễ đổi màu theo theme sáng/tối.
