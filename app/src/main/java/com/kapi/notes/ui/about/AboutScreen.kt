@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kapi.notes.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class AboutFeature(val icon: ImageVector, val title: String, val description: String)

private val features = listOf(
    AboutFeature(Icons.Rounded.Draw, "Viết tay tự nhiên", "Bút, cọ, dạ quang với độ nhạy lực nhấn, tẩy và công cụ lasso chọn/di chuyển."),
    AboutFeature(Icons.Rounded.Straighten, "Thước & Compa ảo", "Vẽ đường thẳng, hình tròn chuẩn xác dù tay không chạm đều."),
    AboutFeature(Icons.Rounded.CloudSync, "Đồng bộ Google Drive", "Dữ liệu lưu trên Drive của chính bạn, không qua máy chủ trung gian."),
    AboutFeature(Icons.Rounded.Link, "Chia sẻ link xem-only", "Gửi ghi chú cho bạn bè/giáo viên chỉ bằng một đường link."),
    AboutFeature(Icons.Rounded.TextFields, "Nhận dạng chữ viết tay", "Chuyển nét vẽ tay thành văn bản ngay trên máy, không cần mạng."),
    AboutFeature(Icons.Rounded.AutoAwesome, "Chèn ảnh/PDF làm nền", "Annotate trực tiếp lên giáo trình hoặc tài liệu PDF/ảnh.")
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Giới thiệu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            com.kapi.notes.ui.common.KapiLogo(modifier = Modifier.size(88.dp), shape = RoundedCornerShape(24.dp))
            Spacer(Modifier.height(16.dp))
            Text("KapiNotes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Phiên bản 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "KapiNotes là ứng dụng ghi chú viết tay dành cho Android, tối ưu cho máy tính bảng có bút cảm ứng (stylus). " +
                        "Hỗ trợ nhiều loại giấy, chèn ảnh/PDF làm nền để chú thích, đồng bộ và sao lưu qua Google Drive của chính bạn.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Tính năng nổi bật",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(12.dp))

            features.forEach { feature ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "© 2026 KapiNotes. Dữ liệu ghi chú luôn thuộc về Google Drive của bạn - không có máy chủ trung gian nào khác.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
