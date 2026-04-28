package com.example.work_calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        UpdateUiState.Idle -> Unit

        is UpdateUiState.Available -> BannerBox(
            color = Color(0xFF2E7D32),
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "새 버전 사용 가능",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B5E20),
                    )
                    Text(
                        "${state.info.releaseName} (${formatBytes(state.info.apkSize)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF1B5E20).copy(alpha = 0.85f),
                    )
                }
                TextButton(onClick = onDismiss) { Text("나중에") }
                Button(onClick = onUpdate) { Text("업데이트") }
            }
        }

        is UpdateUiState.Downloading -> BannerBox(
            color = Color(0xFF1565C0),
            modifier = modifier,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "업데이트 다운로드 중…",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D47A1),
                )
                if (state.total > 0) {
                    val ratio = (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${formatBytes(state.downloaded)} / ${formatBytes(state.total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0D47A1).copy(alpha = 0.85f),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        is UpdateUiState.ReadyToInstall -> BannerBox(
            color = Color(0xFF6A1B9A),
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "다운로드 완료. 설치 화면이 열렸습니다.",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4A148C),
                )
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }

        is UpdateUiState.Failed -> BannerBox(
            color = Color(0xFFC62828),
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "업데이트 실패",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB71C1C),
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB71C1C).copy(alpha = 0.85f),
                    )
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    }
}

@Composable
private fun BannerBox(color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "-"
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}
