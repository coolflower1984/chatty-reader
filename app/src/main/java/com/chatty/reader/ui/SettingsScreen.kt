package com.chatty.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chatty.reader.AppState
import com.chatty.reader.launch.AppLauncher
import com.chatty.reader.launch.SystemGrants
import com.chatty.reader.share.AskAi
import com.chatty.reader.share.ChatGptSender
import com.chatty.reader.watch.FocusIsland
import com.chatty.reader.watch.UsageProbe
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext

    // 这两个权限要在系统设置里改，回来时重新读一次
    var overlayOn by remember { mutableStateOf(SystemGrants.canDrawOverlays(context)) }
    var batteryOn by remember { mutableStateOf(SystemGrants.ignoringBattery(context)) }
    var usageOn by remember { mutableStateOf(UsageProbe.hasAccess(context)) }
    var islandState by remember { mutableStateOf(FocusIsland.state(context)) }
    var islandSupported by remember { mutableStateOf(FocusIsland.supportsIsland()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPreview by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayOn = SystemGrants.canDrawOverlays(context)
                batteryOn = SystemGrants.ignoringBattery(context)
                usageOn = UsageProbe.hasAccess(context)
                islandState = FocusIsland.state(context)
                islandSupported = FocusIsland.supportsIsland()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun label(pkg: String, fallback: String): String =
        if (pkg.isBlank()) fallback else AppLauncher.labelOf(context, pkg) ?: pkg

    fun openPicker(slot: Int) {
        state.pickerSlot = slot
        state.screen = com.chatty.reader.Screen.PICKER
    }

    val askLabel = label(state.config.askPkg, "每次自己选")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---------------- 长按选词：发给谁、发什么 ----------------

            SectionCard {
                SectionLabel("长按选词问 AI")
                Spacer(Modifier.height(4.dp))
                Text(
                    "在阅读页选中一个词或一句，底部会冒出动作条。这里决定那条动作条把文字递给谁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                AppRow(
                    title = "发送给",
                    detail = askLabel,
                    onClick = { openPicker(3) }
                )
                Text(
                    if (state.config.askPkg.trim() == AskAi.YUANBAO_PKG) {
                        "默认腾讯元宝。换成别的 AI 应用也行；清空则每次弹出系统选择器。"
                    } else {
                        "当前用的是「$askLabel」。想回到元宝可以在应用列表里搜「元宝」。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Divider()
                SwitchRow(
                    title = "附带音标和中文释义",
                    detail = "只影响单词表发送；选词问 AI 走的是下面的提示词",
                    checked = state.config.withMeaning,
                    onCheckedChange = { state.config.withMeaning = it }
                )
            }

            Spacer(Modifier.height(12.dp))

            FoldCard(
                title = "发给 AI 的提示词",
                summary = if (state.config.askPrompt.isBlank() || state.config.askPrompt.trim() ==
                    AskAi.DEFAULT_TEMPLATE.trim()
                ) "默认模板" else "已自定义",
                initiallyOpen = false
            ) {
                Text(
                    "选中内容会替换进模板里的占位符：\n" +
                        "{text} 选中的字 · {kind} 单词/句子 · {source} 出处 · " +
                        "{context} 原段落 · {steps} 讲解步骤。\n" +
                        "删掉 {text} 也不会丢内容，正文会自动补在最后。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.config.askPrompt,
                    onValueChange = { state.config.askPrompt = it },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { state.config.resetAskPrompt() }) {
                        Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 单词表发送（老的整组发送流程） ----------------

            SectionCard {
                SectionLabel("整组单词表发送")
                Spacer(Modifier.height(4.dp))
                Text(
                    "首页「发送」走的是这一套：把十个词和固定要求一起交给 ChatGPT。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                AppRow(
                    title = "发送给",
                    detail = label(
                        state.config.gptPkg.ifBlank { ChatGptSender.DEFAULT_PACKAGE },
                        "未指定"
                    ),
                    onClick = { openPicker(2) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showPreview = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("预览这次要发的内容", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            FoldCard(
                title = "整组发送的固定要求",
                summary = if (state.config.requirement.trim() ==
                    com.chatty.reader.AppConfig.DEFAULT_REQUIREMENT.trim()
                ) "默认模板" else "已自定义",
                initiallyOpen = false
            ) {
                Text(
                    "每次都会带上这段话。{words} 会被换成这一组的十个单词；" +
                        "删掉 {words} 的话，单词表自动附在最后。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.config.requirement,
                    onValueChange = { state.config.requirement = it },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { state.config.resetRequirement() }) {
                        Text("恢复默认", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 朗读 ----------------

            SectionCard {
                SectionLabel("单元朗读")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("每个词播几遍", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "三段算一遍，读完停顿 1 秒再往下走",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    (1..3).forEach { n ->
                        NumChip(
                            text = "$n",
                            selected = state.config.wordRepeat == n,
                            onClick = { state.config.wordRepeat = n }
                        )
                    }
                }
                Divider()
                SwitchRow(
                    title = "整单元循环",
                    detail = "词、阅读、题目都播完后从头再来",
                    checked = state.config.loopUnit,
                    onCheckedChange = { state.config.loopUnit = it }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 老流程：折叠起来 ----------------

            FoldCard(
                title = "自动跳转流程",
                summary = "代理工具 → 确认网络 → 自动回来",
                initiallyOpen = false
            ) {
                Text(
                    "点首页「开始准备」后，本应用打开代理工具就退到后台，只负责把你送回来；" +
                        "你在代理里点完开关待够几秒会自动回到本页，再点「打开 X」下拉刷新，刷完又自动回来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                AppRow(
                    title = "代理工具",
                    detail = label(state.config.clashPkg, "未指定"),
                    onClick = { openPicker(0) }
                )
                Divider()
                AppRow(
                    title = "确认网络的应用",
                    detail = label(state.config.xPkg, "未指定"),
                    onClick = { openPicker(1) }
                )
                Divider()
                SwitchRow(
                    title = "打开应用时自动跳到代理",
                    detail = "默认关闭。开了之后一进本应用就会被切到代理工具",
                    checked = state.config.autoOpenClash,
                    onCheckedChange = { state.config.autoOpenClash = it }
                )

                Divider()
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("在代理工具里停留", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.config.clashDwellSeconds} 秒后自动回来",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = state.config.clashDwellSeconds.toFloat(),
                        onValueChange = { state.config.clashDwellSeconds = it.roundToInt() },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                    Text(
                        "这段时间留给你点开关，点完不用手动退出。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Divider()
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("在 X 里停留", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.config.xDwellSeconds} 秒后自动回来",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = state.config.xDwellSeconds.toFloat(),
                        onValueChange = { state.config.xDwellSeconds = it.roundToInt() },
                        valueRange = 3f..30f,
                        steps = 26
                    )
                    Text(
                        "刷新本身没法被感知，用停留时长近似 —— 够你拉一次、扫两眼就行。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            FoldCard(
                title = "权限与通知",
                summary = if (overlayOn && batteryOn && usageOn) "都齐了" else "有项目还没开",
                initiallyOpen = false
            ) {
                PermissionRow(
                    title = "使用情况访问权限",
                    detail = if (usageOn) "已开启，能识别你切到了 X"
                    else "未开启，刷完 X 只能自己切回来",
                    granted = usageOn,
                    action = "去开启",
                    onClick = { UsageProbe.requestAccess(context) }
                )
                Divider()
                PermissionRow(
                    title = "显示在其他应用上层",
                    detail = if (overlayOn) "已开启，能从 X 直接跳回来"
                    else "未开启，系统会拦下这次跳转",
                    granted = overlayOn,
                    action = "去开启",
                    onClick = { SystemGrants.requestOverlay(context) }
                )
                Divider()
                PermissionRow(
                    title = "后台不被限制",
                    detail = if (batteryOn) "已加入省电白名单"
                    else "没加白名单时，系统可能在后台把本应用清掉",
                    granted = batteryOn,
                    action = "去设置",
                    onClick = { SystemGrants.requestIgnoreBattery(context) }
                )
                Divider()
                PermissionRow(
                    title = "状态栏倒计时",
                    detail = when (islandState) {
                        FocusIsland.State.UNSUPPORTED ->
                            "这台机器没有小米焦点通知能力，进度只在普通通知栏里"

                        FocusIsland.State.NO_PERMISSION ->
                            "系统支持，但还没给本应用开「焦点通知」"

                        FocusIsland.State.READY ->
                            if (islandSupported) "已开启，倒计时会显示在超级岛上"
                            else "已开启，倒计时会显示在状态栏焦点通知里"
                    },
                    granted = islandState == FocusIsland.State.READY,
                    action = when (islandState) {
                        FocusIsland.State.UNSUPPORTED -> "不支持"
                        FocusIsland.State.NO_PERMISSION -> "去开启"
                        FocusIsland.State.READY -> "已开启"
                    },
                    onClick = { FocusIsland.openNotificationSettings(context) }
                )
                Text(
                    FocusIsland.diagnose(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Divider()
                SwitchRow(
                    title = "强制尝试焦点通知",
                    detail = "跳过权限探测，无条件带参数发一次。系统认就显示，不认也只是普通通知",
                    checked = state.config.islandForce,
                    onCheckedChange = { state.config.islandForce = it }
                )
                Text(
                    "小米从澎湃 OS 2 起支持焦点通知、OS 3 起支持超级岛。开关在系统「设置 → 通知与控制中心 → " +
                        "十词一组 → 焦点通知」；如果那里没有这一项，说明小米没给这台机器/这个包开通。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider()
                SwitchRow(
                    title = "常驻通知",
                    detail = "留一条低优先级通知，进程不容易被系统清掉；关掉后通知栏更干净，但可能被提前回收",
                    checked = state.config.keepAlive,
                    onCheckedChange = {
                        state.config.keepAlive = it
                        state.syncKeepAlive()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                SectionLabel("关于")
                Spacer(Modifier.height(6.dp))
                Text(
                    "词库是内置的，30 卷 240 组共 2224 个六级词，不需要联网，也不会把你的数据发给任何服务器。\n\n" +
                        "发送时调用系统的“分享”把文字交给你指定的 AI 应用。它不开放“替我发送”的接口，" +
                        "所以最后那一下发送键要你自己点；朗读也是同理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (showPreview) {
        PreviewSheet(state = state, onDismiss = { showPreview = false })
    }
}

/**
 * 折叠起来的一段设置：平时只露标题和一行摘要，点开才铺开内容。
 * 老流程动辄十几行，不折起来会把真正常用的设置埋到下面。
 */
@Composable
private fun FoldCard(
    title: String,
    summary: String,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(0.05f))
            Text(
                if (open) "收起 ▴" else "展开 ▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedVisibility(visible = open) {
            Column {
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun AppRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "更换",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { if (!granted) onClick() }) {
            Text(if (granted) "已开启" else action)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 窄屏上比 FilterChip 省地方的小方块 */
@Composable
private fun NumChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(start = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
