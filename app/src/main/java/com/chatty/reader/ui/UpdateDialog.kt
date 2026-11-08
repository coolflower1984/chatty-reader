package com.chatty.reader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatty.reader.study.ApkUpdate
import com.chatty.reader.study.UpdateRepo

/**
 * 启动时发现新版本的询问框。
 *
 * 三个出口：立刻去下载、这次不装（下次打开再问）、这个版本不再提醒。
 * 两个「不装」的含义不一样，按钮文字上写清楚，别让用户猜。
 */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateRepo.Manifest,
    currentVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    val size = ApkUpdate.sizeText(manifest.apkBytes)
    val sub = buildString {
        append("已装 ").append(currentVersion)
        if (size.isNotEmpty()) append(" · 新版 ").append(size)
    }

    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Column {
                Text(
                    "发现新版本 ${manifest.apkVersionName}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                if (manifest.apkNotes.isNotBlank()) {
                    Text(
                        manifest.apkNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "覆盖安装即可，下载好的单元和生词本都会留着；不更新也能继续用现在这版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("这个版本不再提醒", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("以后再说") }
        }
    )
}
