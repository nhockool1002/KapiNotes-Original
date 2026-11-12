# KapiNotes — Soát xét tuân thủ Google Play (trước khi nộp)

Ngày soát xét: 2026-11-02. Phạm vi: toàn bộ mã nguồn trong repo hiện tại (nhánh `main`).

## Tóm tắt

| Mức độ | Số lượng |
|---|---|
| 🔴 Chặn nộp (phải xử lý trước khi submit) | 1 |
| 🟡 Nên xử lý trước khi nộp | 4 |
| 🟢 Đã sửa trong lần soát xét này | 2 |
| ✅ Đã xử lý sau đó (2026-11-11) | 2 |
| ⚪ Khuyến nghị dài hạn (không chặn) | 2 |

---

## ✅ Đã xử lý sau lần soát xét này (2026-11-11)

### 1. Cấu hình ký bản release (release signing)
Đã có `signingConfigs { release { ... } }` trong `app/build.gradle.kts`, đọc từ `key.properties` (gitignored) khi file đó tồn tại. Keystore thật đã được tạo và đặt ngoài git.

### 2. Google Drive OAuth scope `drive.readonly` (restricted scope)
`DriveAuthManager.kt` từng xin 2 scope `DriveScopes.DRIVE_READONLY` + `DriveScopes.DRIVE_FILE` cho tính năng "Nhập từ Drive" (duyệt/liệt kê file bất kỳ). `drive.readonly` là **restricted scope**, bắt buộc xác minh OAuth consent screen + đánh giá bảo mật CASA trả phí nếu vượt quá 100 test user ở chế độ "In production".

**Đã xử lý:** bỏ hẳn tính năng "Nhập từ Drive" (duyệt folder) cùng scope `drive.readonly` - xem README.md mục 4/5. App giờ chỉ xin `drive.file` (non-sensitive, cho tính năng chia sẻ link view-only) và dùng ID token thuần cho đăng nhập/đồng bộ (sync chuyển sang Cloudflare Worker + R2 riêng, xem `sync-worker/`). Không còn rào cản CASA để publish production cho nhiều người dùng.

---

## 🔴 Mục chặn nộp

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
