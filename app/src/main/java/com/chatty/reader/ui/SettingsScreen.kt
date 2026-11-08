package com.chatty.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
            SectionCard {
                SectionLabel("目标应用")
                Spacer(Modifier.height(4.dp))
                Text(
                    "点一行换一个应用。代理和确认网络这两个是你要用的工具，ChatGPT 保持默认即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                AppRow(
                    title = "代理工具",
                    detail = label(state.config.clashPkg, "未指定"),
                    onClick = {
                        state.pickerSlot = 0
                        state.screen = com.chatty.reader.Screen.PICKER
                    }
                )
                Divider()
                AppRow(
                    title = "确认网络的应用",
                    detail = label(state.config.xPkg, "未指定"),
                    onClick = {
                        state.pickerSlot = 1
                        state.screen = com.chatty.reader.Screen.PICKER
                    }
                )
                Divider()
                AppRow(
                    title = "ChatGPT",
                    detail = label(
                        state.config.gptPkg.ifBlank { ChatGptSender.DEFAULT_PACKAGE },
                        "未指定"
                    ),
                    onClick = {
                        state.pickerSlot = 2
                        state.screen = com.chatty.reader.Screen.PICKER
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                SectionLabel("发送内容")
                Spacer(Modifier.height(6.dp))

                SwitchRow(
                    title = "附带音标和中文释义",
                    detail = "关掉只发单词，让 ChatGPT 完全靠语境讲解",
                    checked = state.config.withMeaning,
                    onCheckedChange = { state.config.withMeaning = it }
                )
                Divider()
                SwitchRow(
                    title = "打开应用时自动跳到代理",
                    detail = "默认关闭。开了之后一进本应用就会被切到 Clash，不想这样保持关闭即可",
                    checked = state.config.autoOpenClash,
                    onCheckedChange = { state.config.autoOpenClash = it }
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                SectionLabel("准备阶段")
                Spacer(Modifier.height(6.dp))
                Text(
                    "点「开始准备」后，本应用打开代理工具就退到后台，只负责把你送回来。" +
                        "你在 Clash 里点完开关待够几秒，会自动回到本页；" +
                        "再点「打开 X」下拉刷新，刷完又自动回来。所有点击都由你自己做。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

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
                    title = "强制尝试",
                    detail = "跳过权限探测，无条件带参数发一次。系统认就显示，不认也只是普通通知，没有副作用",
                    checked = state.config.islandForce,
                    onCheckedChange = { state.config.islandForce = it }
                )
                Text(
                    "小米从澎湃 OS 2 起支持焦点通知、OS 3 起支持超级岛。开关在系统「设置 → 通知与控制中心 → " +
                        "十词一组 → 焦点通知」；如果那里没有这一项，说明小米没给这台机器/这个包开通，属于系统限制。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        "这段时间留给你点开关。点完不用手动退出，到点会自动回到十词一组；" +
                            "觉得来不及就调长一点。",
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
                SectionLabel("固定要求")
                Spacer(Modifier.height(6.dp))
                Text(
                    "每次发送都会带上这段话。里面的 {words} 会被替换成这一组的十个单词；" +
                        "如果你把 {words} 删掉，单词表会自动附在最后。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    state.config.requirement.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { state.config.resetRequirement() }) {
                        Text("恢复默认")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                SectionLabel("关于")
                Spacer(Modifier.height(6.dp))
                Text(
                    "词库是内置的，30 卷 240 组共 2224 个六级词，不需要联网，也不会把你的数据发给任何服务器。\n\n" +
                        "发送时调用系统的“分享”把文字交给 ChatGPT。它不开放“替我发送”的接口，" +
                        "所以最后那一下发送键要你自己点；朗读也是同理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
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

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
