package com.chatty.reader.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatty.reader.study.ContentRepo
import com.chatty.reader.study.UnitStore
import com.chatty.reader.study.UpdateCenter
import com.chatty.reader.study.UpdateRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新包管理：一个包一行，各自下载、各自删除。
 *
 * 这里只动 filesDir/content 里的东西 —— 学习进度、生词本、内置的 Unit 1–10
 * 都不在这里，删包不会碰它们。删除是不可撤销的，所以每次都要过一道确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val online by UpdateCenter.state.collectAsState()

    var have by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var used by remember { mutableLongStateOf(0L) }
    var free by remember { mutableLongStateOf(0L) }
    var sizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var downloaded by remember { mutableIntStateOf(0) }
    var reload by remember { mutableStateOf(0) }

    var pendingDelete by remember { mutableStateOf<UpdateRepo.Pack?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    // 下载结束（或取消）后重新扫一遍，状态和占用都要跟着变
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(online.downloading) {
        if (wasDownloading && !online.downloading) reload++
        wasDownloading = online.downloading
    }

    LaunchedEffect(reload, online.manifest) {
        val m = online.manifest
        val snap = withContext(Dispatchers.IO) {
            val ids = UnitStore.listIds(context).toSet()
            val map = m?.packs?.associate { p -> p.file to ContentRepo.packBytes(context, p.from, p.to) }
                ?: emptyMap()
            val count = ids.count { UnitStore.isDownloaded(context, it) }
            Triple(ids, ContentRepo.installedBytes(context), map) to count
        }
        have = snap.first.first
        used = snap.first.second
        sizes = snap.first.third
        downloaded = snap.second
        free = withContext(Dispatchers.IO) { context.filesDir.usableSpace }
    }

    LaunchedEffect(Unit) {
        if (!online.checkedOnce && !online.downloading) UpdateCenter.check(context)
    }

    val manifest = online.manifest
    val packs = manifest?.packs.orEmpty()
    val totalUnits = manifest?.totalUnits ?: 240
    val busy = online.downloading

    pendingDelete?.let { pack ->
        val n = (pack.from..pack.to).count { it in have }
        val mb = sizes[pack.file] ?: 0L
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 ${pack.title}？") },
            text = {
                Text(
                    "会删掉这 $n 个单元的正文和音频，释放约 ${fmtBytes(mb)}。" +
                        "学习进度和生词本不受影响；以后要用，重新下载这个包即可。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val removed = withContext(Dispatchers.IO) {
                            ContentRepo.removePack(context, pack.from, pack.to)
                        }
                        Toast.makeText(context, "已删除 $removed 个单元", Toast.LENGTH_SHORT).show()
                        pendingDelete = null
                        reload++
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("删除全部已下载单元？") },
            text = {
                Text(
                    "会清空所有下载的 $downloaded 个单元，释放 ${fmtBytes(used)}。" +
                        "内置的 Unit 1–10、学习进度、生词本都保留。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ContentRepo.root(context).deleteRecursively()
                        }
                        Toast.makeText(context, "已清空下载内容", Toast.LENGTH_SHORT).show()
                        confirmWipe = false
                        reload++
                    }
                }) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("更新包", fontWeight = FontWeight.SemiBold)
                        Text(
                            "已装 ${have.size}/$totalUnits 个单元 · 占用 ${fmtBytes(used)} · 可用 ${fmtBytes(free)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (manifest == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (online.checking) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            online.checkError ?: "还没拿到包清单",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { UpdateCenter.check(context) }) { Text("重新检查") }
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("summary") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val done = have.size
                        LinearProgressIndicator(
                            progress = { if (totalUnits == 0) 0f else done.toFloat() / totalUnits },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "内置 10 个单元不占下载空间；下面每个包 10 个单元、约 35 MB。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (busy) {
                            Spacer(Modifier.height(12.dp))
                            val pct = if (online.totalPacks > 0) {
                                (online.donePacks + online.currentPct / 100f) / online.totalPacks
                            } else 0f
                            LinearProgressIndicator(
                                progress = { pct.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${online.currentTitle} ${online.currentPct}% · " +
                                        "${online.donePacks}/${online.totalPacks}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { UpdateCenter.cancel() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) { Text("取消", style = MaterialTheme.typography.labelSmall) }
                            }
                        } else {
                            val missing = packs.filter { p ->
                                (p.from..p.to).any { it !in have }
                            }
                            if (missing.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { UpdateCenter.start(context, missing) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        "下载剩下的 ${missing.size} 个包 · " +
                                            fmtBytes(missing.sumOf { it.bytes })
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(packs, key = { it.file }) { pack ->
                val done = (pack.from..pack.to).count { it in have }
                PackRow(
                    pack = pack,
                    done = done,
                    bytesOnDisk = sizes[pack.file] ?: 0L,
                    downloading = busy && online.currentTitle == pack.title,
                    pct = online.currentPct,
                    busy = busy,
                    onDownload = { UpdateCenter.start(context, listOf(pack)) },
                    onDelete = { pendingDelete = pack }
                )
            }

            if (downloaded > 0) {
                item("wipe") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { confirmWipe = true }, enabled = !busy) {
                            Text(
                                "删除全部下载",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: UpdateRepo.Pack,
    done: Int,
    bytesOnDisk: Long,
    downloading: Boolean,
    pct: Int,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val complete = done >= pack.units && pack.units > 0
    val partial = done in 1 until pack.units

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (complete) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (complete) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pack.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${pack.units} 个单元 · ")
                        if (complete) append("已占 ${fmtBytes(bytesOnDisk)}")
                        else append("下载 ${pack.mb} MB")
                        when {
                            complete -> append(" · 已装完")
                            partial -> append(" · 已装 $done/${pack.units}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (downloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            when {
                downloading -> Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                complete -> TextButton(
                    onClick = onDelete,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onDownload,
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (partial) "续下" else "下载",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (partial) {
                        TextButton(
                            onClick = onDelete,
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "删除",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 字节换成人话：823 MB / 1.2 GB */
private fun fmtBytes(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> "%.1f GB".format(b / 1024f / 1024f / 1024f)
    b >= 1024L * 1024 -> "%.0f MB".format(b / 1024f / 1024f)
    b >= 1024L -> "%.0f KB".format(b / 1024f)
    else -> "$b B"
}
