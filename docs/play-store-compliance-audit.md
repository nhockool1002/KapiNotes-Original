# KapiNotes — Soát xét tuân thủ Google Play (trước khi nộp)

Ngày soát xét: 2026-11-02. Phạm vi: toàn bộ mã nguồn trong repo hiện tại (nhánh `main`).

## Tóm tắt

| Mức độ | Số lượng |
|---|---|
| 🔴 Chặn nộp (phải xử lý trước khi submit) | 3 |
| 🟡 Nên xử lý trước khi nộp | 4 |
| 🟢 Đã sửa trong lần soát xét này | 2 |
| ⚪ Khuyến nghị dài hạn (không chặn) | 2 |

---

## 🔴 Mục chặn nộp

### 1. Chưa có cấu hình ký bản release (release signing)
`app/build.gradle.kts` → `buildTypes { release { ... } }` **không có `signingConfig`**. Play Console bắt buộc mọi bản build nộp lên phải được ký (dùng Play App Signing là chuẩn hiện tại: bạn giữ một "upload key" để ký file nộp, Google giữ "app signing key" để ký bản phát hành thật cho người dùng).

**Cần làm (phía bạn, không thể tự động hóa an toàn vì cần keystore thật):**
1. Tạo upload keystore: `keytool -genkeypair -v -keystore upload-keystore.jks -alias kapinotes -keyalg RSA -keysize 2048 -validity 10000`
2. Lưu `upload-keystore.jks` **ngoài git** (đã có `*.jks` trong `.gitignore`, tốt).
3. Thêm `signingConfigs { release { ... } }` vào `build.gradle.kts`, đọc mật khẩu từ file `key.properties` không commit (đã có trong `.gitignore`).
4. Khi tạo app mới trên Play Console, chọn "Let Google manage and protect your app signing key" (Play App Signing) — đây là lựa chọn mặc định và được khuyến nghị.

### 2. Google Drive OAuth scope `drive.readonly` là "restricted scope"
`DriveAuthManager.kt` yêu cầu 2 scope: `DriveScopes.DRIVE_READONLY` + `DriveScopes.DRIVE_FILE`.
- `drive.file` — scope **không hạn chế**, chỉ truy cập file do chính app tạo/mở — an toàn, không cần xác minh thêm.
- `drive.readonly` — scope **hạn chế** (restricted scope): cho phép đọc TẤT CẢ file trong Drive của người dùng. Google yêu cầu **xác minh OAuth consent screen** (và có thể cả đánh giá bảo mật CASA hàng năm) nếu ứng dụng vượt quá 100 người dùng thử nghiệm và dùng scope này ở chế độ "In production".

**Cần làm:**
- Vào Google Cloud Console → OAuth consent screen → nộp để xác minh app (giải thích rõ vì sao cần đọc toàn bộ Drive — ví dụ: tính năng "Nhập từ Drive" cho phép chọn file bất kỳ do người dùng chọn, không chỉ file app tạo ra).
- Nếu có thể, cân nhắc đổi sang Google Drive **Picker API** (chỉ cấp quyền đúng file người dùng chọn qua UI chọn file của Google, dùng scope `drive.file` là đủ) để **tránh hoàn toàn** việc cần xin scope hạn chế — giảm rủi ro và thời gian chờ xét duyệt. Đây là thay đổi kiến trúc, nên cân nhắc làm riêng nếu muốn né bước xác minh.
- Việc này độc lập với Play Console, nằm ở Google Cloud Console — cần làm trước khi public app cho >100 user.

### 3. Chưa có Chính sách quyền riêng tư (Privacy Policy) công khai
App xin quyền Internet, ghi âm (`RECORD_AUDIO`), và truy cập Google Drive/tài khoản Google — Play Console **bắt buộc** nhập URL Chính sách quyền riêng tư cho mọi app xử lý dữ liệu cá nhân.

**Cần làm:** Host một trang Privacy Policy công khai (tĩnh, có thể là GitHub Pages miễn phí) và điền URL đó vào Play Console. Mình đã soạn sẵn nội dung song ngữ trong tài liệu mô tả ứng dụng gửi kèm — bạn chỉ cần host nó ở một URL.

---

## 🟡 Nên xử lý trước khi nộp

### 4. `isMinifyEnabled = false` cho bản release
APK/AAB release hiện không bật R8/ProGuard shrink — dẫn đến file nộp lớn hơn cần thiết và không tối ưu. Không phải lỗi chặn nộp, nhưng Play Console sẽ cảnh báo "cỡ app lớn hơn khuyến nghị". Bật minify cần test kỹ (Room/Hilt/kotlinx.serialization dùng reflection, dễ vỡ nếu thiếu proguard-rules phù hợp) — nên làm ở một đợt riêng với thời gian test đầy đủ trên bản release thật, không nên bật vội trong lần này.

### 5. Chưa chuẩn bị nội dung "Data Safety" (Play Console)
Play Console yêu cầu khai báo chi tiết dữ liệu thu thập/chia sẻ. Dựa trên mã nguồn, dữ liệu thực tế app xử lý:
- **Email, tên hiển thị, ảnh đại diện** (từ Google Sign-In) — lưu cục bộ trên máy, dùng để hiển thị + đồng bộ.
- **Nội dung ghi chú** (nét vẽ, ảnh, văn bản, ghi âm) — lưu cục bộ + đồng bộ lên **chính Google Drive của người dùng**, không có máy chủ trung gian nào khác.
- **Không** thu thập dữ liệu phân tích (analytics), quảng cáo, hay bên thứ ba nào khác ngoài Google (Sign-In + Drive).

Mình đã liệt kê sẵn mục trả lời cho form Data Safety trong tài liệu mô tả ứng dụng gửi kèm, để bạn điền trực tiếp vào Play Console.

### 6. `RECORD_AUDIO` cần khai báo lý do sử dụng rõ ràng
Dùng cho tính năng "Ghi âm bài giảng". Đã có xin quyền runtime đúng chuẩn (`ContextCompat.checkSelfPermission` + launcher xin quyền) — về mặt kỹ thuật ổn. Khi điền Play Console, mục "App content" → khai báo rõ mục đích ghi âm để tránh bị gắn cờ review thủ công.

### 7. Chưa đóng gói dạng Android App Bundle (.aab) khi nộp
Play Console yêu cầu nộp `.aab`, không nhận `.apk` cho app mới. Lệnh build đúng là:
```bash
./gradlew bundleRelease
```
(thay vì `assembleRelease` — lệnh này chỉ tạo APK test, không dùng để nộp Play Console). File AAB xuất ra ở `app/build/outputs/bundle/release/app-release.aab`.

---

## 🟢 Đã sửa trong lần soát xét này

- **Thêm quy tắc loại trừ sao lưu đám mây (`backup_rules.xml` + `data_extraction_rules.xml`)**: trước đây `android:allowBackup="true"` sao lưu *toàn bộ* dữ liệu app lên Google Backup mà không loại trừ gì — bao gồm cả file phiên đăng nhập (email, tên, id file đồng bộ Drive). Đã thêm quy tắc loại trừ riêng file này khỏi sao lưu/chuyển máy, giữ lại ghi chú + cài đặt (đúng ý định: khôi phục ghi chú khi cài lại máy, nhưng không khôi phục một phiên đăng nhập cũ/có thể sai).
- **Xác nhận không có secret/API key gắn cứng trong mã nguồn** — đã quét toàn repo, không phát hiện.

---

## ⚪ Khuyến nghị dài hạn (không chặn nộp)

- Gỡ dependency `okhttp3:logging-interceptor` nếu không dùng tới (hiện có trong `build.gradle.kts` nhưng không thấy chỗ nào khởi tạo `HttpLoggingInterceptor` trong mã nguồn) — giảm nhẹ kích thước app.
- Cân nhắc đổi kiến trúc Drive sang Picker API (xem mục 2) để tránh hoàn toàn restricted scope về lâu dài.
- Dọn file ảnh mồ côi khi xóa ghi chú/trang (đã trao đổi ở phiên làm việc trước) — không phải yêu cầu Play Store nhưng ảnh hưởng tốt tới trải nghiệm & đồng bộ.

---

## Những gì đã kiểm tra và ổn, không cần sửa

- `targetSdk = 35`, `compileSdk = 35` — đáp ứng yêu cầu targetSdk mới nhất của Play Console tại thời điểm soát xét.
- Icon adaptive đầy đủ, không cần icon PNG fallback (minSdk 26 ≥ 26, ngưỡng adaptive icon).
- Không dùng cleartext traffic (không khai báo `usesCleartextTraffic`, mặc định chặn với targetSdk ≥ 28 — toàn bộ API gọi qua HTTPS).
- Không dùng quyền nguy hiểm/nhạy cảm nào khác ngoài `RECORD_AUDIO` (không có vị trí, camera, danh bạ, SMS...).
- Không có WebView, không có quảng cáo/SDK bên thứ ba theo dõi người dùng.
- `FileProvider` cấu hình đúng chuẩn (`exported=false`, `grantUriPermissions`).
- Không chạy ghi âm nền dạng foreground service — không cần khai báo loại foreground service cho Android 14+.
- Ứng dụng hoạt động bình thường với cả bút cảm ứng và ngón tay (đáp ứng `uses-feature touchscreen required=true`).
