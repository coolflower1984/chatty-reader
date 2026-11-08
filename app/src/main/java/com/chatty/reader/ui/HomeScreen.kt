package com.chatty.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatty.reader.AppState
import com.chatty.reader.PermissionGate
import com.chatty.reader.Screen
import com.chatty.reader.launch.AppLauncher
import com.chatty.reader.launch.SystemGrants
import com.chatty.reader.study.StudyProgress
import com.chatty.reader.study.UnitStore
import com.chatty.reader.vocab.Word
import com.chatty.reader.vocab.WordGroup
import com.chatty.reader.watch.ChainStage
import com.chatty.reader.watch.ChainWatcher
import com.chatty.reader.watch.UsageProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenVocab: () -> Unit = {}
) {
    var showGroupPicker by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    // 老流程默认收起：首页留给教材
    var showClassic by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var libCount by remember { mutableStateOf(0) }
    var learnedWords by remember { mutableStateOf(0) }
    var firstUnit by remember { mutableStateOf(1) }
    var last by remember { mutableStateOf<StudyProgress.Last?>(null) }
    var doneUnits by remember { mutableStateOf(0) }

    // 每次回到首页都重读：从阅读页回来，进度和「继续」都要是新的
    LaunchedEffect(Unit) {
        val snap = withContext(Dispatchers.IO) {
            val progress = StudyProgress.snapshot(context)
            // 只为学过的单元读一次内容，判断「整单元学完」；没学过的必然没学完
            val full = progress.count { (id, learnedSet) ->
                val unit = UnitStore.load(context, id) ?: return@count false
                learnedSet.size >= unit.words.size
            }
            HomeSnapshot(
                ids = UnitStore.listIds(context),
                learned = StudyProgress.learnedCount(context),
                last = StudyProgress.last(context),
                doneUnits = full
            )
        }
        libCount = snap.ids.size
        firstUnit = snap.ids.firstOrNull() ?: 1
        learnedWords = snap.learned
        last = snap.last
        doneUnits = snap.doneUnits
    }

    LaunchedEffect(state.toast) {
        val message = state.toast
        if (message != null) {
            snackbar.showSnackbar(message)
            state.dismissToast()
        }
    }

    val dialogContext = LocalContext.current.applicationContext
    state.permissionGate?.let { gate ->
        AlertDialog(
            onDismissRequest = { state.permissionGate = null },
            title = { Text("还差一项权限") },
            text = {
                Text(
                    when (gate) {
                        PermissionGate.USAGE_ACCESS ->
                            "安卓不允许应用自己看“你正在用哪个 App”，必须你手动授权。\n\n" +
                                "给「使用情况访问权限」之后，它才能知道你切到了 X；" +
                                "没有这项，刷完 X 只能你自己切回来。\n\n" +
                                "这项权限只用来读“当前是哪个应用在前台”，不读任何内容。"

                        PermissionGate.OVERLAY ->
                            "安卓 10 起，应用在后台不能自己跳到前台。\n\n" +
                                "你还在 X 里的时候本应用处于后台，没有「显示在其他应用上层」就拉不回来，看起来像没反应。\n\n" +
                                "打开这一项之后，刷完 X 才会自动跳回十词一组。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.permissionGate = null
                    when (gate) {
                        PermissionGate.USAGE_ACCESS -> UsageProbe.requestAccess(dialogContext)
                        PermissionGate.OVERLAY -> SystemGrants.requestOverlay(dialogContext)
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { state.permissionGate = null }) { Text("先不用") }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "十词一组",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "$libCount 个单元 · 每单元十词 · 离线可听",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.Star, contentDescription = "生词本")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            // 发送按钮属于老流程，收起来时一起收
            if (showClassic) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { state.sendToGpt() },
                            enabled = state.current != null,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("发送到 ChatGPT", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }

            state.loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.loadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { state.load() }) { Text("重试") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("continue") {
                    ContinueCard(
                        last = last,
                        learnedWords = learnedWords,
                        onClick = { state.openUnit(last?.unitId ?: firstUnit) }
                    )
                }

                item("library") {
                    LibraryEntryCard(
                        unitCount = libCount,
                        learnedWords = learnedWords,
                        onClick = onOpenLibrary
                    )
                }

                item("stats") {
                    StatsStrip(
                        learnedWords = learnedWords,
                        doneUnits = doneUnits,
                        totalUnits = libCount
                    )
                }

                item("classicHeader") {
                    ClassicHeader(expanded = showClassic, onToggle = { showClassic = !showClassic })
                }

                if (showClassic) {
                    state.current?.let { group ->
                        item("group") {
                            GroupCard(
                                group = group,
                                onClick = { showGroupPicker = true },
                                onNext = { state.nextGroup() }
                            )
                        }

                        item("words") {
                            WordsCard(group = group, withMeaning = state.config.withMeaning)
                        }

                        item("prompt") {
                            RequirementCard(state = state, onPreview = { showPreview = true })
                        }

                        item("prep") {
                            PrepCard(state = state)
                        }

                        item("tip") {
                            Text(
                                "切到 ChatGPT 后点一下发送键；它回答完，你再点朗读。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGroupPicker) {
        GroupPickerSheet(state = state, onDismiss = { showGroupPicker = false })
    }

    if (showPreview) {
        PreviewSheet(state = state, onDismiss = { showPreview = false })
    }
}

private data class HomeSnapshot(
    val ids: List<Int>,
    val learned: Int,
    val last: StudyProgress.Last?,
    val doneUnits: Int
)

// ---------------------------------------------------------------- 继续学习

@Composable
private fun ContinueCard(
    last: StudyProgress.Last?,
    learnedWords: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (last == null) "开始" else "继续学习",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (last == null) "从 Unit 1 开始"
                    else "Unit ${last.unitId} · 第 ${last.wordIdx} 个词",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (last != null) {
                            append(ago(last.at))
                            append(" · ")
                        }
                        append(if (learnedWords > 0) "已学 $learnedWords 个词" else "三步讲解，听完就算学过")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

/** 「刚刚 / 12 分钟前 / 3 小时前 / 2 天前」；时钟被调乱也不至于显示负数 */
private fun ago(at: Long): String {
    val gap = (System.currentTimeMillis() - at).coerceAtLeast(0L)
    return when {
        gap < 60_000 -> "刚刚"
        gap < 3_600_000 -> "${gap / 60_000} 分钟前"
        gap < 86_400_000 -> "${gap / 3_600_000} 小时前"
        else -> "${gap / 86_400_000} 天前"
    }
}

// ---------------------------------------------------------------- 教材入口

@Composable
private fun LibraryEntryCard(
    unitCount: Int,
    learnedWords: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "内置教材",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "$unitCount 个单元 · ${if (learnedWords > 0) "已学 $learnedWords 个词" else "全部离线，不用联网"}" +
                        " · 每单元十词",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------------------------------------------------------------- 学习概览

@Composable
private fun StatsStrip(learnedWords: Int, doneUnits: Int, totalUnits: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCell("已学词", learnedWords.toString())
            StatCell("学完单元", doneUnits.toString())
            StatCell("待学单元", (totalUnits - doneUnits).coerceAtLeast(0).toString())
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- 经典流程

@Composable
private fun ClassicHeader(expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "经典流程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Clash → 刷 X → 回 App → 发 ChatGPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- 词组卡

@Composable
private fun GroupCard(
    group: WordGroup,
    onClick: () -> Unit,
    onNext: () -> Unit
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("当前词组")
                Spacer(Modifier.height(4.dp))
                Text(
                    group.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    group.subtitle + " · 点击换组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "下一组",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 十词卡

@Composable
private fun WordsCard(group: WordGroup, withMeaning: Boolean) {
    var expandedNo by remember(group.listNo, group.groupNo) { mutableIntStateOf(-1) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("本组单词")
            Spacer(Modifier.weight(1f))
            if (group.words.size < 10) {
                Text(
                    "本卷只剩 ${group.words.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        group.words.forEachIndexed { index, word ->
            WordRow(
                word = word,
                expanded = expandedNo == word.no,
                onToggle = { expandedNo = if (expandedNo == word.no) -1 else word.no }
            )
            if (index < group.words.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }

        if (!withMeaning) {
            Spacer(Modifier.height(8.dp))
            Text(
                "点任意一行看释义。发送时默认只带单词，让 ChatGPT 用语境去讲。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WordRow(word: Word, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(word.no),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    word.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
                if (word.phonetic.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "[${word.phonetic}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AnimatedVisibility(visible = expanded && word.meaning.isNotBlank()) {
                Text(
                    word.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 固定要求

@Composable
private fun RequirementCard(state: AppState, onPreview: () -> Unit) {
    var editing by remember { mutableStateOf(false) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("固定要求")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "收起" else "编辑")
            }
        }

        if (editing) {
            OutlinedTextField(
                value = state.config.requirement,
                onValueChange = { state.config.requirement = it },
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { state.config.resetRequirement() }) {
                    Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Text(
                state.config.requirement.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPreview, contentPadding = PaddingValues(0.dp)) {
                Text("预览发送内容", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (state.config.withMeaning) "含中文释义" else "只发单词",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- 准备步骤

@Composable
private fun PrepCard(state: AppState) {
    val context = LocalContext.current.applicationContext
    val clashLabel = AppLauncher.labelOf(context, state.config.clashPkg) ?: "未指定"
    val checkerLabel = AppLauncher.labelOf(context, state.config.xPkg) ?: "未指定"

    val stage = ChainWatcher.stage
    val showProgress = stage == ChainStage.TO_CLASH || stage == ChainStage.AT_CLASH ||
        stage == ChainStage.WAITING_X || stage == ChainStage.IN_X ||
        stage == ChainStage.DONE || stage == ChainStage.FAILED

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("准备阶段")
            Spacer(Modifier.weight(1f))
            when {
                ChainWatcher.running -> TextButton(onClick = { state.stopPrepare() }) {
                    Text("停止", style = MaterialTheme.typography.labelMedium)
                }

                stage != ChainStage.IDLE -> TextButton(onClick = { state.resetPrep() }) {
                    Text("重来", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (showProgress) {
            ProgressRow(stage = stage, message = ChainWatcher.message)

            // 从 Clash 被送回来之后，下一步就是去 X 刷一下
            if (stage == ChainStage.WAITING_X) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { state.openCheckerFromChain() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("打开 $checkerLabel 刷一下", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "在 $checkerLabel 里停 ${state.config.xDwellSeconds} 秒，会自动回到本页；" +
                        "不想刷了也可以直接点下面的发送。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (stage == ChainStage.FAILED) {
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = { state.startPrepare() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("再试一次", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Button(
                onClick = { state.startPrepare() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("开始准备", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "会打开 $clashLabel，待 ${state.config.clashDwellSeconds} 秒后自动回到本页；" +
                    "再点「打开 $checkerLabel」刷 ${state.config.xDwellSeconds} 秒，又回到本页。" +
                    "开关和下拉刷新都由你手动做。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!ChainWatcher.running) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            StepRow(
                index = 1,
                title = "打开代理，开启 VPN",
                detail = clashLabel,
                done = state.vpnDone,
                actionText = when {
                    state.config.clashPkg.isBlank() -> "去选择"
                    state.vpnDone -> "再打开"
                    else -> "打开"
                },
                onAction = {
                    if (state.config.clashPkg.isBlank()) state.screen = Screen.SETTINGS
                    else state.openClash()
                },
                onToggle = { state.toggleVpnDone() }
            )
            Spacer(Modifier.height(4.dp))
            StepRow(
                index = 2,
                title = "刷新一下，确认能上网",
                detail = checkerLabel,
                done = state.checkDone,
                actionText = when {
                    state.config.xPkg.isBlank() -> "去选择"
                    state.checkDone -> "再打开"
                    else -> "打开"
                },
                onAction = {
                    if (state.config.xPkg.isBlank()) state.screen = Screen.SETTINGS
                    else state.openChecker()
                },
                onToggle = { state.toggleCheckDone() }
            )
        }
    }
}

@Composable
private fun ProgressRow(stage: ChainStage, message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (stage) {
            ChainStage.DONE -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )

            ChainStage.FAILED -> Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )

            else -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            message.ifBlank { "进行中" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (stage == ChainStage.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StepRow(
    index: Int,
    title: String,
    detail: String,
    done: Boolean,
    actionText: String,
    onAction: () -> Unit,
    onToggle: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已完成",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            index.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onAction) {
            Text(actionText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ---------------------------------------------------------------- 公共组件

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp
    )
}
