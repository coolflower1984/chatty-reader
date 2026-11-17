package com.chatty.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.chatty.reader.study.LibraryIndex
import com.chatty.reader.study.ListRow
import com.chatty.reader.study.UnitSection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Slider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.chatty.reader.study.UpdateCenter
import com.chatty.reader.study.UpdateRepo
import com.chatty.reader.study.PlayNode
import com.chatty.reader.study.listIndexOf
import com.chatty.reader.study.playNodes
import com.chatty.reader.study.safeIndex
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf

/* ---------------- 单元列表 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenPacks: () -> Unit = {}
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

    // 找单元用的三件套：搜索词、折叠了哪些段、要滚到哪个位置
    var query by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapseInited by remember { mutableStateOf(false) }
    var jumpKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

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
    val byId = remember(units) { units.associateBy { it.id } }

    val sections = remember(units, progress) { LibraryIndex.sections(units, progress) }
    // 搜索只在已下载（也就是本地有内容）的单元里做，免得点进去是空音频
    val hits = remember(units, query) {
        if (query.isBlank()) null else LibraryIndex.search(units, query)
    }
    val rows = remember(sections, collapsed, hits) { LibraryIndex.rows(sections, collapsed, hits) }

    // 第一次进来只展开「上次学的那一段」，其余收起；之后用户的展开/收起不再被覆盖
    LaunchedEffect(sections, lastUnitId) {
        if (collapseInited || sections.isEmpty()) return@LaunchedEffect
        val keep = lastUnitId ?: sections.first().from
        collapsed = sections.filter { keep !in it.unitIds }.map { it.key }.toSet()
        collapseInited = true
    }

    // 胶囊和「回到上次」都只是设一个目标，真正的滚动放在这里：
    // rows 变了（比如刚把某段展开）才会算得出正确下标
    LaunchedEffect(rows, jumpKey) {
        val key = jumpKey ?: return@LaunchedEffect
        val at = if (key.startsWith("s:")) {
            LibraryIndex.rowIndexOfSection(rows, key.removePrefix("s:"))
        } else {
            LibraryIndex.rowIndexOfUnit(rows, key.removePrefix("u:").toIntOrNull() ?: -1)
        }
        if (at >= 0) listState.animateScrollToItem(at + 1) // +1 是上方的资源卡
        jumpKey = null
    }

    val scrolledAway by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    fun jumpTo(id: Int) {
        sections.firstOrNull { id in it.unitIds }?.let { s ->
            if (s.key in collapsed) collapsed = collapsed - s.key
        }
        jumpKey = "u:$id"
    }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 搜索常驻在列表上方：滑到多深都够得着，不用先滚回顶部
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = {
                        Text("搜单元号 / 单词 / 释义", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "清空",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                // 每 10 个单元一个胶囊，点一下直接滚到那一段；有进度的小点标出来
                if (query.isBlank() && sections.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sections, key = { it.key }) { s ->
                            val dot: (@Composable () -> Unit)? = if (s.learned > 0) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            } else null

                            AssistChip(
                                onClick = {
                                    if (s.key in collapsed) collapsed = collapsed - s.key
                                    jumpKey = "s:${s.key}"
                                },
                                label = {
                                    Text(
                                        "${s.from}–${s.to}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = dot
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hits == null) {
                        item("online") {
                            ResourceCard(
                                state = online,
                                pending = pendingPacks,
                                haveCount = units.size,
                                onCheck = { UpdateCenter.check(context) },
                                onDownload = { confirmAll = true },
                                onCancel = { UpdateCenter.cancel() },
                                onManage = onOpenPacks
                            )
                        }
                    } else {
                        item("hits") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (hits.isEmpty()) "没找到「${query.trim()}」"
                                    else "找到 ${hits.size} 个单元",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { query = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("清除", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    rows.forEach { row ->
                        when (row) {
                            is ListRow.Header -> item(key = "s-${row.section.key}") {
                                SectionHeader(
                                    section = row.section,
                                    collapsed = row.collapsed,
                                    pack = online.manifest?.packOf(row.section.from),
                                    haveIds = haveIds,
                                    busy = online.downloading,
                                    onToggle = {
                                        collapsed = if (row.section.key in collapsed) {
                                            collapsed - row.section.key
                                        } else {
                                            collapsed + row.section.key
                                        }
                                    },
                                    onDownload = { p -> UpdateCenter.start(context, listOf(p)) }
                                )
                            }

                            is ListRow.UnitLine -> item(key = "u-${row.unitId}") {
                                val unit = byId[row.unitId] ?: return@item
                                val dl = UnitStore.isDownloaded(context, unit.id)
                                UnitRow(
                                    unit = unit,
                                    downloaded = dl,
                                    learned = progress[unit.id]?.size ?: 0,
                                    isLast = unit.id == lastUnitId,
                                    match = row.note,
                                    onClick = { onOpen(unit.id) },
                                    onReset = { pendingReset = unit.id },
                                    onDelete = if (dl) ({ pendingDelete = unit.id }) else null
                                )
                            }
                        }
                    }

                    if (hits != null && hits.isEmpty()) {
                        item("noHit") {
                            Text(
                                "搜索只覆盖已经下载到本机的单元。换个单元号、单词或中文释义试试，" +
                                    "也可以先把对应的包下下来。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (hits == null && learnedTotal > 0) {
                        item("resetAll") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
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

                    if (hits == null) {
                        item("import") {
                            Surface(
                                onClick = {
                                    picker.launch(
                                        arrayOf("application/zip", "application/octet-stream", "*/*")
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
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

            // 翻远了才浮出来，免得一进页面就盖住列表
            if (lastUnitId != null && query.isBlank() && scrolledAway) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { jumpTo(lastUnitId) },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        "↑ 回到上次",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
    onCancel: () -> Unit,
    onManage: () -> Unit
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("下载全部 · %.0f MB".format(missingMb))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onManage) {
                        Text("管理更新包", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (state.checkError != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onCheck,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text("重试") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onManage) {
                        Text("管理更新包", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onManage,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("管理更新包 →", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 每一段（默认十个单元，正好对上更新包）的标题：点一下折叠，右侧是下载入口 */
@Composable
private fun SectionHeader(
    section: UnitSection,
    collapsed: Boolean,
    pack: UpdateRepo.Pack?,
    haveIds: Set<Int>,
    busy: Boolean,
    onToggle: () -> Unit,
    onDownload: (UpdateRepo.Pack) -> Unit
) {
    val complete = pack != null && (pack.from..pack.to).all { it in haveIds }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 核心图标包里没有展开/收起那一对，直接用字形，省一个依赖
        Text(
            if (collapsed) "▸" else "▾",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Unit ${section.from}–${section.to}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${section.unitIds.size} 个单元 · 已学 ${section.learned}/${section.total}" +
                    if (collapsed) " · 已收起" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
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
    match: String? = null,
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
                            match ?: "${unit.words.size} 词 · ${unit.reading.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (match != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (match != null) FontWeight.Medium else FontWeight.Normal,
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

    // 上次停在列表的哪一项：进来直接落回去，不用再从第一个词往下翻
    val savedScroll = remember(unit.id) { StudyProgress.scroll(appContext, unit.id) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScroll?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedScroll?.second ?: 0
    )
    val scope = rememberCoroutineScope()

    // 存的是列表下标，不是词号：停在阅读第二段、第 3 题也能原样回去
    fun saveScroll() {
        StudyProgress.setScroll(
            appContext,
            unit.id,
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset
        )
    }

    // 断点要在播放器之前声明：播完那一下回调会把它清回 -1
    var breakpoint by remember(unit.id) {
        mutableIntStateOf(StudyProgress.playPos(appContext, unit.id))
    }

    val player = remember(unit.id) {
        // 每开一段就记一笔：一个词三段里听过任意一段，就算学过
        SegmentPlayer(
            appContext,
            onSegment = { key -> StudyProgress.onSegment(appContext, key) },
            // 连播断点：每换一段就存一次，最多几十次写盘
            onPosition = { i -> StudyProgress.setPlayPos(appContext, unit.id, i) },
            // 自然播完就把断点清掉，下次从第一段开始
            onFinished = {
                StudyProgress.clearPlayPos(appContext, unit.id)
                breakpoint = -1
            }
        ).also {
            it.wordRepeat = state.config.wordRepeat
            it.loopUnit = state.config.loopUnit
        }
    }
    // 设置改了立刻作用于正在播的这一轮，不用退出去重进
    LaunchedEffect(state.config.wordRepeat, state.config.loopUnit) {
        player.wordRepeat = state.config.wordRepeat
        player.loopUnit = state.config.loopUnit
    }
    DisposableEffect(unit.id) {
        onDispose {
            saveScroll()
            player.release()
        }
    }

    // 滑一次停一下都写盘太勤，等滚动停稳再存；进程被杀也丢不了太多
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (!moving) saveScroll()
        }
    }

    // 整单元连播的顺序：词的三段 → 阅读 → 5 道题
    val nodes = remember(unit.id) { unit.playNodes() }
    var showList by remember { mutableStateOf(false) }

    // 播放时页面跟着当前段走。自己拖过列表就先让 6 秒，免得想回看上一段被拽回来
    var userScrolledAt by remember { mutableLongStateOf(0L) }

    // 只认手指拖动；程序自己滚的时候不会触发，所以不会把自己也当成「用户想停下」
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) userScrolledAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(player.index, player.playing, player.wholeUnit) {
        // 只有整单元连播时 index 才对应 nodes 的下标；单段播放是它自己的小队列
        if (!player.playing || !player.wholeUnit) return@LaunchedEffect
        if (System.currentTimeMillis() - userScrolledAt < 6_000) return@LaunchedEffect
        val target = nodes.listIndexOf(player.index, unit.words.size) ?: return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == target }) return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    val progressTick = StudyProgress.version
    val learned = remember(unit.id, progressTick) {
        StudyProgress.played(appContext, unit.id)
    }

    // 进单元就把「上次学到哪」挪到这里，续读点落在第一个还没学的词
    LaunchedEffect(unit.id) {
        StudyProgress.markOpen(appContext, unit.id, unit.words.map { it.idx })
    }

    // 「回到第 n 个词」的目标：进单元那一刻第一个还没学过的词（都学完了就最后一个）
    val resumeOrdinal = remember(unit.id) {
        val done = StudyProgress.played(appContext, unit.id)
        val i = unit.words.indexOfFirst { it.idx !in done }
        (if (i >= 0) i else unit.words.lastIndex).coerceAtLeast(0)
    }
    // 列表第 0 项是断点卡的位置，所以词的下标要 +1
    val resumeIndex = resumeOrdinal + 1
    val farFromResume by remember(resumeIndex) {
        derivedStateOf { abs(listState.firstVisibleItemIndex - resumeIndex) >= 4 }
    }
    var showOutline by remember { mutableStateOf(false) }

    val answers = remember(unit.id) { mutableStateMapOf<Int, Int>() }
    val scale = state.config.fontScale
    var selection by remember { mutableStateOf<SelectionPayload?>(null) }
    var showFont by remember { mutableStateOf(false) }
    var showPlayOptions by remember { mutableStateOf(false) }

    // 选区打开时，返回键先收动作条，而不是退出阅读
    BackHandler(enabled = selection != null) { selection = null }

    if (showFont) {
        FontScaleDialog(
            current = scale,
            onChange = { state.config.fontScale = it },
            onDismiss = { showFont = false }
        )
    }

    if (showPlayOptions) {
        PlayOptionsDialog(
            repeat = state.config.wordRepeat,
            loop = state.config.loopUnit,
            onRepeat = { state.config.wordRepeat = it },
            onLoop = { state.config.loopUnit = it },
            onDismiss = { showPlayOptions = false }
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
                    // 连播时按钮让位给底部播放条，免得两个入口打架
                    if (!player.wholeUnit) {
                        IconButton(
                            onClick = {
                                player.playFrom(
                                    nodes.map { it.key },
                                    nodes.safeIndex(breakpoint),
                                    unit.words.size
                                )
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("▶", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    // 目录：十个词 + 阅读 + 五道题，点谁跳谁
                    IconButton(
                        onClick = { showOutline = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = "目录",
                            modifier = Modifier.size(21.dp)
                        )
                    }
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
                    IconButton(onClick = onOpenVocab, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "生词本",
                            modifier = Modifier.size(21.dp)
                        )
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 底部留白给选区动作条，免得最后一段被挡住
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第 0 项永远留给断点卡：它出不出现都不改变后面卡片的下标，
                // 否则「恢复上次滚动位置」会整体错开一项
                item(key = "breakpoint") {
                    if (!player.wholeUnit && breakpoint in 1 until nodes.size) {
                        BreakpointCard(
                            node = nodes[breakpoint],
                            position = breakpoint + 1,
                            total = nodes.size,
                            onResume = {
                                player.playFrom(nodes.map { it.key }, breakpoint, unit.words.size)
                            },
                            onDismiss = {
                                StudyProgress.clearPlayPos(appContext, unit.id)
                                breakpoint = -1
                            }
                        )
                    }
                }

                items(unit.words, key = { it.idx }) { word ->
                    WordCard(unit, word, player, scale, learned = word.idx in learned) { selection = it }
                }

                item {
                    ReadingCard(unit, player, scale) { selection = it }
                }

                questions(unit, player, answers, scale) { selection = it }
            }

            // 翻出去几屏才浮出来，一键回到“该学的那一格”，不打扰正在读的段落
            if (farFromResume && !player.wholeUnit && selection == null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            scope.launch { listState.animateScrollToItem(resumeIndex) }
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    ),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        "↑ 回到第 ${resumeOrdinal + 1} 个词",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (player.wholeUnit && player.current != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    PlayBar(
                        nodes = nodes,
                        player = player,
                        repeatTimes = state.config.wordRepeat,
                        loopUnit = state.config.loopUnit,
                        onOpenList = { showList = true },
                        onOpenOptions = { showPlayOptions = true },
                        onClose = {
                            breakpoint = player.index
                            player.stop()
                        }
                    )
                }
            } else selection?.let { payload ->
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

    if (showList) {
        PlayListSheet(
            nodes = nodes,
            current = player.index,
            onPick = { i ->
                player.jumpTo(i)
                breakpoint = i
                showList = false
            },
            onDismiss = { showList = false }
        )
    }

    if (showOutline) {
        UnitOutlineSheet(
            unit = unit,
            learned = learned,
            onPick = { index ->
                showOutline = false
                scope.launch { listState.animateScrollToItem(index) }
            },
            onDismiss = { showOutline = false }
        )
    }
}

/** 单元目录：十个词 + 阅读 + 五道题，点一条就滚过去 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitOutlineSheet(
    unit: StudyUnit,
    learned: Set<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 列表第 0 项是断点卡，正文从 1 开始
    val readingAt = 1 + unit.words.size
    val questionAt = readingAt + 1

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "head") {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
                    Text(
                        "本单元目录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "已学 ${learned.size}/${unit.words.size} · 点一条就跳过去",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "g-word") {
                OutlineGroup("单词")
            }
            unit.words.forEachIndexed { i, w ->
                item(key = "w-${w.idx}") {
                    OutlineRow(
                        no = "%02d".format(i + 1),
                        title = w.word,
                        subtitle = w.zh.lineSequence().first(),
                        done = w.idx in learned,
                        onClick = { onPick(1 + i) }
                    )
                }
            }

            item(key = "g-reading") { OutlineGroup("阅读") }
            item(key = "reading") {
                OutlineRow(
                    no = "R",
                    title = unit.reading.title,
                    subtitle = "${unit.reading.paragraphs.size} 段 · ${unit.reading.questions.size} 题",
                    done = false,
                    onClick = { onPick(readingAt) }
                )
            }

            item(key = "g-question") { OutlineGroup("题目") }
            unit.reading.questions.forEachIndexed { q, question ->
                item(key = "q-$q") {
                    OutlineRow(
                        no = "%02d".format(q + 1),
                        title = "第 ${q + 1} 题",
                        subtitle = question.prompt,
                        done = false,
                        onClick = { onPick(questionAt + q) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlineGroup(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun OutlineRow(
    no: String,
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            no,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (done) {
            Icon(
                Icons.Default.Check,
                contentDescription = "已学",
                modifier = Modifier.size(15.dp),
                tint = Color(0xFF3FB950)
            )
        }
    }
}

/** 上次听到一半时的入口；不自动播放，避免一进单元就出声 */
@Composable
private fun BreakpointCard(
    node: PlayNode,
    position: Int,
    total: Int,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "上次听到第 $position/$total 段",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    node.shortLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onResume) { Text("继续", style = MaterialTheme.typography.labelMedium) }
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) { Text("×", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** 连播时贴在底部的迷你播放条；点「清单」展开全部节点 */
@Composable
private fun PlayBar(
    nodes: List<PlayNode>,
    player: SegmentPlayer,
    repeatTimes: Int,
    loopUnit: Boolean,
    onOpenList: () -> Unit,
    onOpenOptions: () -> Unit,
    onClose: () -> Unit
) {
    val i = player.index
    val node = nodes.getOrNull(i)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    node?.shortLabel().orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (player.inGap) {
                    Text(
                        "停顿…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    "${i + 1}/${nodes.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { if (nodes.isEmpty()) 0f else (i + 1f) / nodes.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { player.previous() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("上一段", style = MaterialTheme.typography.labelSmall) }

                TextButton(
                    onClick = { player.toggle() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (player.playing) "暂停" else "继续",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = { player.next() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("下一段", style = MaterialTheme.typography.labelSmall) }

                Spacer(Modifier.weight(1f))

                // 当前设置一目了然：每词几遍 / 是否整单元循环
                TextButton(
                    onClick = onOpenOptions,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "每词×$repeatTimes${if (loopUnit) " 循环" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                TextButton(
                    onClick = onOpenList,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("清单", style = MaterialTheme.typography.labelSmall) }

                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("×", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** 全单元节点清单：同一个词的三段收成一行，点开再选具体哪一步 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayListSheet(
    nodes: List<PlayNode>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 每个词的三段在 nodes 里是连续的三项，折成一组
    val wordGroups = remember(nodes) {
        nodes.withIndex()
            .filter { it.value.group == PlayNode.Group.WORD }
            .groupBy { it.value.wordIdx }
            .values
            .map { it.first().index to it.last().index }
    }
    val currentWord = nodes.getOrNull(current)
        ?.takeIf { it.group == PlayNode.Group.WORD }
        ?.let { w -> wordGroups.indexOfFirst { current in it.first..it.second } }
        ?: -1
    // 默认只展开正在播的那个词；播放走到下一个词时自动跟着展开
    var expanded by remember { mutableStateOf(if (currentWord >= 0) setOf(currentWord) else emptySet()) }
    LaunchedEffect(currentWord) {
        if (currentWord >= 0) expanded = expanded + currentWord
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "head") {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "整单元 · ${nodes.size} 段",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (wordGroups.isEmpty()) "点任意一段就跳过去接着播"
                            else "点词展开三步，点具体某一步直接跳过去",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (wordGroups.isNotEmpty()) {
                        val allOpen = expanded.size >= wordGroups.size
                        TextButton(
                            onClick = {
                                expanded = if (allOpen) emptySet() else wordGroups.indices.toSet()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (allOpen) "全部收起" else "全部展开",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (wordGroups.isNotEmpty()) {
                item(key = "g-word") { PlayListGroupTitle("单词 · 三步讲解") }

                wordGroups.forEachIndexed { gi, range ->
                    val first = nodes[range.first]
                    val open = gi in expanded
                    val playingHere = current in range.first..range.second

                    item(key = "wg-$gi") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (open) expanded - gi else expanded + gi
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (open) "▾" else "▸",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                "%02d".format(gi + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (playingHere) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    first.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (playingHere) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (playingHere) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (playingHere) "正在播 · ${nodes[current].step}" else "${range.second - range.first + 1} 段",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (playingHere) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (open) {
                        for (i in range.first..range.second) {
                            val n = nodes[i]
                            val playing = i == current
                            item(key = n.key) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(i) }
                                        .padding(start = 60.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        n.step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (playing) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (playing) {
                                        Text(
                                            "正在播",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val readingIndex = nodes.indexOfFirst { it.group == PlayNode.Group.READING }
            if (readingIndex >= 0) {
                item(key = "g-reading") { PlayListGroupTitle("阅读全文") }
                item(key = nodes[readingIndex].key) {
                    PlayListJumpRow(
                        title = nodes[readingIndex].title,
                        subtitle = nodes[readingIndex].step,
                        playing = current == readingIndex,
                        onClick = { onPick(readingIndex) }
                    )
                }
            }

            val questionAt = nodes.indexOfFirst { it.group == PlayNode.Group.QUESTION }
            if (questionAt >= 0) {
                item(key = "g-question") { PlayListGroupTitle("题目") }
                for (i in questionAt until nodes.size) {
                    val n = nodes[i]
                    item(key = n.key) {
                        PlayListJumpRow(
                            title = n.title,
                            subtitle = n.step,
                            playing = i == current,
                            onClick = { onPick(i) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayListGroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun PlayListJumpRow(
    title: String,
    subtitle: String,
    playing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (playing) "▶" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

/**
 * 连播设置：每个词播几遍、整单元是否循环。
 *
 * 重复只作用于词（① → ② → ③ 算一遍），阅读和题目永远只播一次；
 * 段与段之间固定空 1 秒，留出跟读和回想的时间。
 */
@Composable
private fun PlayOptionsDialog(
    repeat: Int,
    loop: Boolean,
    onRepeat: (Int) -> Unit,
    onLoop: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("连播设置") },
        text = {
            Column {
                Text(
                    "每个词播几遍",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { n ->
                        if (n == repeat) {
                            Button(onClick = { onRepeat(n) }) { Text("$n 遍") }
                        } else {
                            OutlinedButton(onClick = { onRepeat(n) }) { Text("$n 遍") }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "① 猜一猜 → ② 释义 → ③ 六级语境 算一遍；选 2 遍就是这个词连着听两轮再进下一个词。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("整单元循环", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "题目播完后从头再来，适合磨耳朵；关掉则停在末尾",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = loop, onCheckedChange = onLoop)
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "每段读完后空 1 秒再进下一段，这条固定不设开关。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
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
