package com.chatty.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.chatty.reader.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.sp
import com.chatty.reader.study.ContentRepo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatty.reader.study.Question
import com.chatty.reader.study.SegmentPlayer
import com.chatty.reader.study.StudyProgress
import com.chatty.reader.study.Unit as StudyUnit
import com.chatty.reader.study.UnitStore
import com.chatty.reader.study.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Slider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.chatty.reader.study.UpdateCenter
import com.chatty.reader.study.UpdateRepo

/* ---------------- 单元列表 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpen: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var units by remember { mutableStateOf<List<StudyUnit>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    var bytes by remember { mutableStateOf(0L) }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var pendingPacks by remember { mutableStateOf<List<UpdateRepo.Pack>>(emptyList()) }
    var confirmAll by remember { mutableStateOf(false) }
    var pendingReset by remember { mutableStateOf<Int?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }

    val online by UpdateCenter.state.collectAsState()

    val progressTick = StudyProgress.version
    val progress = remember(units, progressTick) { StudyProgress.snapshot(context) }
    val learnedTotal = remember(progress) { progress.values.sumOf { it.size } }
    val lastUnitId = remember(progressTick) { StudyProgress.last(context)?.unitId }


    // 下载结束（或取消）后重扫目录，新单元立刻出现在列表里
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(online.downloading) {
        if (wasDownloading && !online.downloading) reload++
        wasDownloading = online.downloading
    }

    LaunchedEffect(online.message) {
        online.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            UpdateCenter.consumeMessage()
        }
    }

    // 进页面先默默查一次清单；没网也只是状态栏显示一句话，不打扰
    LaunchedEffect(Unit) {
        if (!online.checkedOnce && !online.downloading) UpdateCenter.check(context)
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 Unit $id？") },
            text = { Text("只会删掉下载的内容，内置单元删不掉。想再听可以在线重新下载。") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = ContentRepo.remove(context, id)
                    pendingDelete = null
                    Toast.makeText(
                        context,
                        if (ok) "已删除 Unit $id" else "删除失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    reload++
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    pendingReset?.let { id ->
        val n = progress[id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text("重置 Unit $id 的进度？") },
            text = { Text("这 $n 个词的「学过」标记会清掉，回到未学状态；单元内容和音频都不动，随时可以重听。") },
            confirmButton = {
                TextButton(onClick = {
                    StudyProgress.resetUnit(context, id)
                    pendingReset = null
                    Toast.makeText(context, "Unit $id 的进度已重置", Toast.LENGTH_SHORT).show()
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null }) { Text("取消") }
            }
        )
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text("清空全部学习进度？") },
            text = {
                Text(
                    "所有单元里「学过」的标记和「上次学到哪」都会清掉，已下载的单元、生词本都不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    StudyProgress.resetAll(context)
                    confirmResetAll = false
                    Toast.makeText(context, "学习进度已清空", Toast.LENGTH_SHORT).show()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) { Text("取消") }
            }
        )
    }

    if (confirmAll) {
        val mb = pendingPacks.sumOf { it.bytes } / 1024f / 1024f
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("下载 ${pendingPacks.size} 个更新包？") },
            text = {
                Text(
                    "共 ${pendingPacks.sumOf { it.units }} 个单元，约 %.0f MB。".format(mb) +
                        "建议在 Wi-Fi 下进行，并让 App 留在前台；中途断了也不要紧，" +
                        "已经装好的包不会丢，再点一次会从剩下的接着下。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    UpdateCenter.start(context, pendingPacks)
                }) { Text("开始下载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) { Text("取消") }
            }
        )
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { ContentRepo.importZip(context, uri) }
            importing = false
            Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
            if (r.ok) reload++
        }
    }

    LaunchedEffect(reload) {
        loading = true
        val all = withContext(Dispatchers.IO) {
            UnitStore.listIds(context).mapNotNull { UnitStore.load(context, it) }
        }
        units = all
        bytes = withContext(Dispatchers.IO) { ContentRepo.installedBytes(context) }
        loading = false
    }

    // 清单或本地内容一变，就重算「还差哪些包」
    LaunchedEffect(reload, online.manifest) {
        val m = online.manifest
        pendingPacks = if (m == null) emptyList()
        else withContext(Dispatchers.IO) { UpdateCenter.missing(context, m) }
    }

    val words = units.sumOf { it.words.size }
    val downloaded = remember(units) { units.count { UnitStore.isDownloaded(context, it.id) } }
    val haveIds = remember(units) { units.map { it.id }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("内置教材", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (loading) "正在载入…" else {
                                val extra = if (downloaded > 0) "，其中 $downloaded 个已下载" else ""
                                "${units.size} 个单元 · $words 个词 · 已学 $learnedTotal · 离线$extra"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { UpdateCenter.check(context) },
                        enabled = !online.checking && !online.downloading
                    ) {
                        if (online.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "检查更新")
                        }
                    }
                    IconButton(
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        enabled = !importing
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = "从文件导入")
                        }
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
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("online") {
                ResourceCard(
                    state = online,
                    pending = pendingPacks,
                    haveCount = units.size,
                    onCheck = { UpdateCenter.check(context) },
                    onDownload = { confirmAll = true },
                    onCancel = { UpdateCenter.cancel() }
                )
            }

            items(units, key = { it.id }) { unit ->
                val groupStart = (unit.id - 1) % 10 == 0 || unit.id == units.first().id
                if (groupStart) {
                    GroupHeader(
                        unitId = unit.id,
                        lastId = units.last().id,
                        pack = online.manifest?.packOf(unit.id),
                        haveIds = haveIds,
                        busy = online.downloading,
                        onDownload = { p -> UpdateCenter.start(context, listOf(p)) }
                    )
                }
                val dl = UnitStore.isDownloaded(context, unit.id)
                UnitRow(
                    unit = unit,
                    downloaded = dl,
                    learned = progress[unit.id]?.size ?: 0,
                    isLast = unit.id == lastUnitId,
                    onClick = { onOpen(unit.id) },
                    onReset = { pendingReset = unit.id },
                    onDelete = if (dl) ({ pendingDelete = unit.id }) else null
                )
            }

            if (learnedTotal > 0) {
                item("resetAll") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { confirmResetAll = true }) {
                            Text(
                                "清空全部学习进度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item("import") {
                Surface(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "从文件导入",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "已经用浏览器下好了 unitpack-*.zip，从这里选一个装上" +
                                if (bytes > 0) " · 本地已占 ${"%.1f".format(bytes / 1024f / 1024f)} MB" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 列表顶部：在线资源总览 + 一键下载 */
@Composable
private fun ResourceCard(
    state: UpdateCenter.State,
    pending: List<UpdateRepo.Pack>,
    haveCount: Int,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val missingUnits = pending.sumOf { it.units }
    val missingMb = pending.sumOf { it.bytes } / 1024f / 1024f
    val newApk = state.manifest?.takeIf {
        it.apkVersionCode > BuildConfig.VERSION_CODE && it.apkFile.isNotBlank()
    }

    val status = when {
        state.checking -> "正在检查…"
        state.checkError != null -> "检查失败"
        state.manifest == null -> "未检查"
        pending.isEmpty() -> "已是最新"
        else -> "${pending.size} 个包待下载"
    }
    val detail = when {
        state.checkError != null -> state.checkError
        state.manifest == null -> "已装 ${haveCount} 个单元 · 点右上角 ↻ 检查更新"
        pending.isEmpty() -> "全部 ${state.manifest?.totalUnits ?: 240} 个单元就位，断网也能学"
        else -> "已装 $haveCount 个 · 还有 $missingUnits 个 · 约 %.0f MB · 建议 Wi-Fi".format(missingMb)
    } + (
        state.lastError?.takeIf { !state.downloading && state.failed > 0 }
            ?.let { "\n上次有 ${state.failed} 个包没成功：$it" } ?: ""
        )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "在线资源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.checkError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 服务端发了新版 APK 才出现；同版本不会打扰
            if (newApk != null) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateRepo.apkUrl(newApk.apkFile)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "App 有新版 ${newApk.apkVersionName} → 去下载",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (state.downloading) {
                val pct = if (state.totalPacks > 0) {
                    (state.donePacks + state.currentPct / 100f) / state.totalPacks
                } else 0f
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.donePacks}/${state.totalPacks} · ${state.currentTitle} ${state.currentPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("取消", style = MaterialTheme.typography.labelSmall) }
                }
            } else if (pending.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("下载全部 · %.0f MB".format(missingMb))
                }
            } else if (state.checkError != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCheck,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) { Text("重试") }
            }
        }
    }
}

/** 每组十单元的小标题，右侧带这一组的下载入口 */
@Composable
private fun GroupHeader(
    unitId: Int,
    lastId: Int,
    pack: UpdateRepo.Pack?,
    haveIds: Set<Int>,
    busy: Boolean,
    onDownload: (UpdateRepo.Pack) -> Unit
) {
    val label = pack?.let { "Unit ${it.from}–${it.to}" }
        ?: "Unit $unitId–${minOf(unitId + 9, lastId)}"
    val complete = pack != null && (pack.from..pack.to).all { it in haveIds }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (pack != null) {
            if (complete) {
                Text(
                    "已装",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            } else {
                TextButton(
                    onClick = { onDownload(pack) },
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        "下载 · ${pack.mb} MB",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnitRow(
    unit: StudyUnit,
    downloaded: Boolean,
    learned: Int,
    isLast: Boolean,
    onClick: () -> Unit,
    onReset: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val total = unit.words.size
    val done = learned.coerceIn(0, total)
    var menu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 13.dp, bottom = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            unit.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isLast) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "继续",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (downloaded) {
                            Text(
                                "已下载",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            "${unit.words.size} 词 · ${unit.reading.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("重置本单元进度") },
                            enabled = done > 0,
                            onClick = {
                                menu = false
                                onReset()
                            }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("删除下载") },
                                onClick = {
                                    menu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = if (done == total && total > 0) Color(0xFF3FB950)
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (done == total && total > 0) "已学完" else "$done/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done == total && total > 0) Color(0xFF3FB950)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- 单元阅读页 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    unit: StudyUnit,
    state: com.chatty.reader.AppState,
    onBack: () -> Unit,
    onOpenVocab: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val player = remember(unit.id) {
        // 每开一段就记一笔：一个词三段里听过任意一段，就算学过
        SegmentPlayer(appContext, onSegment = { key -> StudyProgress.onSegment(appContext, key) })
    }
    DisposableEffect(unit.id) { onDispose { player.release() } }

    val progressTick = StudyProgress.version
    val learned = remember(unit.id, progressTick) {
        StudyProgress.played(appContext, unit.id)
    }

    // 进单元就把「上次学到哪」挪到这里，续读点落在第一个还没学的词
    LaunchedEffect(unit.id) {
        StudyProgress.markOpen(appContext, unit.id, unit.words.map { it.idx })
    }

    val answers = remember(unit.id) { mutableStateMapOf<Int, Int>() }
    val scale = state.config.fontScale
    var selection by remember { mutableStateOf<SelectionPayload?>(null) }
    var showFont by remember { mutableStateOf(false) }

    // 选区打开时，返回键先收动作条，而不是退出阅读
    BackHandler(enabled = selection != null) { selection = null }

    if (showFont) {
        FontScaleDialog(
            current = scale,
            onChange = { state.config.fontScale = it },
            onDismiss = { showFont = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(unit.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${unit.words.size} 词 · ${unit.reading.questions.size} 题 · " +
                                "已学 ${learned.size}/${unit.words.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        player.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showFont = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "A",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.Star, contentDescription = "生词本")
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
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 底部留白给选区动作条，免得最后一段被挡住
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(unit.words, key = { it.idx }) { word ->
                    WordCard(unit, word, player, scale, learned = word.idx in learned) { selection = it }
                }

                item {
                    ReadingCard(unit, player, scale) { selection = it }
                }

                questions(unit, player, answers, scale) { selection = it }
            }

            selection?.let { payload ->
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    SelectionActionBar(
                        payload = payload,
                        onClose = {
                            payload.dismiss()
                            selection = null
                        }
                    )
                }
            }
        }
    }
}

/** 字号只影响正文，标题栏保持原样 */
private fun TextStyle.scaledBy(k: Float): TextStyle {
    if (k == 1f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * k else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * k else lineHeight
    )
}

@Composable
private fun FontScaleDialog(
    current: Float,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读字号") },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        "The cost of convenience is not always obvious at first glance.",
                        style = MaterialTheme.typography.bodyMedium.scaledBy(current),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = current,
                    onValueChange = onChange,
                    valueRange = 0.85f..1.5f,
                    steps = 12
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "小",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(current * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "大",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(1f) }) { Text("恢复默认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@Composable
private fun WordCard(
    unit: StudyUnit,
    word: Word,
    player: SegmentPlayer,
    scale: Float,
    learned: Boolean,
    onSelect: (SelectionPayload?) -> Unit
) {
    val keys = listOf(
        unit.segKey(word, 1),
        unit.segKey(word, 2),
        unit.segKey(word, 3)
    )
    val active = player.current != null && player.current in keys

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (active) 0.55f else 0.3f),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(24.dp)) {
                    if (learned) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已学过",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            "%02d".format(word.idx),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    word.word,
                    style = MaterialTheme.typography.headlineSmall.scaledBy(scale),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "/${word.phonetic}/",
                    style = MaterialTheme.typography.labelMedium.scaledBy(scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                PlayChip(active = active, playing = active && player.playing) {
                    player.play(keys)
                }
            }

            if (word.zh.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    word.zh,
                    style = MaterialTheme.typography.bodySmall.scaledBy(scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            StepRow("① 猜一猜", word.guess, word.guessQ, unit.segKey(word, 1), player, unit.id, scale, onSelect)
            StepRow("② 释义", word.meaningEn, null, unit.segKey(word, 2), player, unit.id, scale, onSelect)
            StepRow("③ 六级语境", word.context, null, unit.segKey(word, 3), player, unit.id, scale, onSelect, last = true)
        }
    }
}

@Composable
private fun StepRow(
    label: String,
    body: String,
    tail: String?,
    key: String,
    player: SegmentPlayer,
    unitId: Int,
    scale: Float,
    onSelect: (SelectionPayload?) -> Unit,
    last: Boolean = false
) {
    val playing = player.current == key && player.playing
    val loaded = player.current == key

    Column(modifier = Modifier.padding(bottom = if (last) 0.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.weight(1f))
            MiniPlay(playing = playing, loaded = loaded) { player.play(listOf(key)) }
        }
        Spacer(Modifier.height(4.dp))
        SelectableText(
            text = body,
            unitId = unitId,
            style = MaterialTheme.typography.bodyMedium.scaledBy(scale),
            color = if (loaded) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            onSelection = onSelect
        )
        if (!tail.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            SelectableText(
                text = tail,
                unitId = unitId,
                style = MaterialTheme.typography.bodyMedium.scaledBy(scale),
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
                onSelection = onSelect
            )
        }
    }
}

@Composable
private fun ReadingCard(
    unit: StudyUnit,
    player: SegmentPlayer,
    scale: Float,
    onSelect: (SelectionPayload?) -> Unit
) {
    val key = unit.readingKey()
    val playing = player.current == key && player.playing

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        unit.reading.title,
                        style = MaterialTheme.typography.titleMedium.scaledBy(scale),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "全文朗读",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MiniPlay(playing = playing, loaded = player.current == key, big = true) {
                    player.play(listOf(key))
                }
            }

            Spacer(Modifier.height(10.dp))
            unit.reading.paragraphs.forEach { p ->
                SelectableText(
                    text = p,
                    unitId = unit.id,
                    style = MaterialTheme.typography.bodyMedium.scaledBy(scale),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp),
                    onSelection = onSelect
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.questions(
    unit: StudyUnit,
    player: SegmentPlayer,
    answers: MutableMap<Int, Int>,
    scale: Float,
    onSelect: (SelectionPayload?) -> Unit
) {
    unit.reading.questions.forEachIndexed { i, q ->
        item(key = "q$i") {
            QuestionCard(
                index = i,
                total = unit.reading.questions.size,
                question = q,
                audioKey = unit.questionKey(i),
                player = player,
                unitId = unit.id,
                scale = scale,
                picked = answers[i],
                onPick = { answers[i] = it },
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    total: Int,
    question: Question,
    audioKey: String,
    player: SegmentPlayer,
    unitId: Int,
    scale: Float,
    picked: Int?,
    onPick: (Int) -> Unit,
    onSelect: (SelectionPayload?) -> Unit
) {
    val playing = player.current == audioKey && player.playing
    val done = picked != null

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Q${index + 1} / $total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.weight(1f))
                MiniPlay(playing = playing, loaded = player.current == audioKey) {
                    player.play(listOf(audioKey))
                }
            }

            Spacer(Modifier.height(6.dp))
            SelectableText(
                text = question.prompt,
                unitId = unitId,
                style = MaterialTheme.typography.bodyLarge.scaledBy(scale),
                color = MaterialTheme.colorScheme.onSurface,
                onSelection = onSelect
            )
            Spacer(Modifier.height(10.dp))

            question.options.forEachIndexed { i, option ->
                val isAnswer = i == question.answer
                val isPick = picked == i
                val border = when {
                    !done -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    isAnswer -> Color(0xFF3FB950)
                    isPick -> Color(0xFFF85149)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                }
                val bg = when {
                    !done -> Color.Transparent
                    isAnswer -> Color(0xFF3FB950).copy(alpha = 0.12f)
                    isPick -> Color(0xFFF85149).copy(alpha = 0.12f)
                    else -> Color.Transparent
                }

                Surface(
                    onClick = { if (!done) onPick(i) },
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            "${('A' + i)}",
                            style = MaterialTheme.typography.bodyMedium.scaledBy(scale),
                            fontWeight = FontWeight.SemiBold,
                            color = if (done && isAnswer) Color(0xFF3FB950)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyMedium.scaledBy(scale),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (done && question.why.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (picked == question.answer) "答对了" else "不是这条",
                    style = MaterialTheme.typography.labelMedium.scaledBy(scale),
                    fontWeight = FontWeight.SemiBold,
                    color = if (picked == question.answer) Color(0xFF3FB950) else Color(0xFFF85149)
                )
                Spacer(Modifier.height(2.dp))
                SelectableText(
                    text = question.why,
                    unitId = unitId,
                    style = MaterialTheme.typography.bodySmall.scaledBy(scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onSelection = onSelect
                )
            }
        }
    }
}

/* ---------------- 播放按钮 ---------------- */

@Composable
private fun PlayChip(active: Boolean, playing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = if (playing) "暂停" else "连播",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun MiniPlay(playing: Boolean, loaded: Boolean, big: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (loaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (loaded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.size(if (big) 34.dp else 28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (playing) "‖" else "▶",
                style = if (big) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.labelSmall,
                color = if (loaded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
