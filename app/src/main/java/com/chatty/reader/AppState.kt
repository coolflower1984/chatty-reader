package com.chatty.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatty.reader.launch.AppLauncher
import com.chatty.reader.launch.SystemGrants
import com.chatty.reader.share.ChatGptSender
import com.chatty.reader.vocab.PromptBuilder
import com.chatty.reader.vocab.Vocabulary
import com.chatty.reader.vocab.WordGroup
import com.chatty.reader.watch.ChainService
import com.chatty.reader.watch.ChainStage
import com.chatty.reader.watch.ChainWatcher
import com.chatty.reader.watch.UsageProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SETTINGS, PICKER, LIBRARY, READER, VOCAB }

/** 发送前要走的步骤，顺序即界面从上到下的顺序 */
enum class PrepStep { VPN, CHECK }

/** 缺哪一项权限需要引导 */
enum class PermissionGate { USAGE_ACCESS, OVERLAY }

/**
 * 运行期状态与业务编排。界面只读它、只调它的方法。
 */
class AppState(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val config = AppConfig(appContext)

    var screen by mutableStateOf(Screen.HOME)

    /** 从哪一页点进生词本的，返回时回那一页 */
    var vocabReturn by mutableStateOf(Screen.HOME)

    /** 正在看的单元；null 表示还在载入 */
    var currentUnit by mutableStateOf<com.chatty.reader.study.Unit?>(null)
    var readingUnitId by mutableStateOf(1)
    /** 0 = 代理工具，1 = 确认网络的应用，2 = ChatGPT */
    var pickerSlot by mutableStateOf(0)

    // ---------- 词库 ----------

    var groups by mutableStateOf<List<WordGroup>>(emptyList())
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)

    private var loadJob: Job? = null

    val current: WordGroup?
        get() = if (groups.isEmpty()) null else Vocabulary.find(groups, config.listNo, config.groupNo)

    val currentListGroups: List<WordGroup>
        get() = Vocabulary.groupsOf(groups, config.listNo)

    val lists: List<Int>
        get() = Vocabulary.allLists(groups)

    // ---------- 发送前的步骤 ----------

    var vpnDone by mutableStateOf(false)
    var checkDone by mutableStateOf(false)

    /** 两项系统权限的实时状态，每次回到前台重读（用户可能刚在系统设置里给完） */
    var usageAccessOn by mutableStateOf(UsageProbe.hasAccess(appContext))
    var overlayOn by mutableStateOf(SystemGrants.canDrawOverlays(appContext))

    /** 缺权限时弹说明，避免“开始准备”跑到一半才失败 */
    var permissionGate by mutableStateOf<PermissionGate?>(null)

    private var pending: PrepStep? = null

    var toast by mutableStateOf<String?>(null)

    // ---------- 加载 ----------

    fun load() {
        if (groups.isNotEmpty()) return
        // 用 Job 判断是否在加载中：失败后 Job 已结束，重试才不会被挡回去
        if (loadJob?.isActive == true) return

        loading = true
        loadJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { Vocabulary.load(appContext) }
            }
            result.onSuccess {
                groups = it
                loadError = null
                normalizeSelection()
            }.onFailure {
                loadError = "词库读取失败：${it.message ?: "未知错误"}"
            }
            loading = false
        }
    }

    /** 上次记住的组若在新词库里不存在，退回第一组，避免空白界面 */
    private fun normalizeSelection() {
        if (groups.isEmpty()) return
        val exists = groups.any {
            it.listNo == config.listNo && it.groupNo == config.groupNo
        }
        if (!exists) {
            val first = groups.first()
            config.listNo = first.listNo
            config.groupNo = first.groupNo
        }
    }

    // ---------- 选词 ----------

    fun selectGroup(listNo: Int, groupNo: Int) {
        config.listNo = listNo
        config.groupNo = groupNo
    }

    fun nextGroup() {
        val c = current ?: return
        val (listNo, groupNo) = Vocabulary.next(groups, c)
        selectGroup(listNo, groupNo)
    }

    // ---------- 拼装待发送文本 ----------

    fun buildText(): String {
        val c = current ?: return ""
        return PromptBuilder.build(config.requirement, c.words, config.withMeaning)
    }

    // ---------- 手动打开（保留着，随时可用） ----------

    /** 打开代理工具。用户在那里手动开 VPN，返回后本页自动打勾。 */
    fun openClash() {
        if (config.clashPkg.isBlank()) {
            toast = "还没指定代理工具，先到设置里选一下"
            screen = Screen.SETTINGS
            return
        }
        if (AppLauncher.launch(appContext, config.clashPkg)) {
            pending = PrepStep.VPN
        } else {
            toast = "打不开 ${AppLauncher.labelOf(appContext, config.clashPkg) ?: "这个应用"}，检查是否已安装"
        }
    }

    /** 打开平时用来确认外网的应用（X、浏览器都行）。 */
    fun openChecker() {
        if (config.xPkg.isBlank()) {
            toast = "还没指定用来确认网络的应用，先到设置里选一下"
            screen = Screen.SETTINGS
            return
        }
        if (AppLauncher.launch(appContext, config.xPkg)) {
            pending = PrepStep.CHECK
        } else {
            toast = "打不开 ${AppLauncher.labelOf(appContext, config.xPkg) ?: "这个应用"}，检查是否已安装"
        }
    }

    /**
     * 从 Clash 被送回来之后，点一下就去 X。
     *
     * 这时盯梢还开着，不能走 [openChecker]（那会把 pending 覆盖掉、
     * 回来时重复打勾）。只要把 X 拉到前台，剩下交给服务数秒。
     */
    fun openCheckerFromChain() {
        if (config.xPkg.isBlank()) {
            toast = "还没指定用来确认网络的应用，先到设置里选一下"
            screen = Screen.SETTINGS
            return
        }
        if (!AppLauncher.launch(appContext, config.xPkg)) {
            toast = "打不开 ${AppLauncher.labelOf(appContext, config.xPkg) ?: "这个应用"}，检查是否已安装"
            return
        }
        pending = null
        toast = null
    }

    fun toggleVpnDone() {
        vpnDone = !vpnDone
        pending = null
    }

    fun toggleCheckDone() {
        checkDone = !checkDone
        pending = null
    }

    // ---------- 开始准备 ----------

    /**
     * 打开代理工具，并让后台开始盯着应用切换。
     *
     * 打开 Clash 这一步必须在这里做：此刻本应用在前台，系统允许跳转。
     * 之后你手动开 VPN、手动开 X、手动刷新，刷完服务会把你送回本页。
     *
     * 不申请无障碍 —— 所有点击都由你完成。
     */
    fun startPrepare(): Boolean {
        val group = current
        if (group == null || group.words.isEmpty()) {
            toast = "这一组没有词，先换一组"
            return false
        }
        if (buildText().isBlank()) {
            toast = "发送内容是空的"
            return false
        }
        if (config.clashPkg.isBlank()) {
            toast = "先指定代理工具"
            screen = Screen.SETTINGS
            return false
        }
        if (config.xPkg.isBlank()) {
            toast = "先指定确认网络用的应用，比如 X"
            screen = Screen.SETTINGS
            return false
        }

        // 两项权限缺一不可：没有使用情况权限就看不见你切到了哪，
        // 没有悬浮窗权限就没法在你还在 X 里的时候把本应用拉到前台
        if (!UsageProbe.hasAccess(appContext)) {
            permissionGate = PermissionGate.USAGE_ACCESS
            return false
        }
        if (!SystemGrants.canDrawOverlays(appContext)) {
            permissionGate = PermissionGate.OVERLAY
            return false
        }

        ChainService.watch(
            context = appContext,
            clashPkg = config.clashPkg,
            xPkg = config.xPkg,
            dwellMs = config.xDwellSeconds * 1000L,
            clashDwellMs = config.clashDwellSeconds * 1000L
        )

        if (!AppLauncher.launch(appContext, config.clashPkg)) {
            ChainService.idle(appContext)
            ChainWatcher.reset()
            toast = "打不开代理工具，检查设置里选的包名"
            return false
        }

        vpnDone = true
        return true
    }

    /** 中途放弃盯梢，通知栏回到待命 */
    fun stopPrepare() {
        ChainService.idle(appContext)
        ChainWatcher.reset()
    }

    // ---------- 回到前台 ----------

    /**
     * 从别的应用回到本页时调用：刷新权限状态；刚才打开的那一步视为完成。
     */
    fun onForeground() {
        usageAccessOn = UsageProbe.hasAccess(appContext)
        overlayOn = SystemGrants.canDrawOverlays(appContext)

        when (ChainWatcher.stage) {
            // 刚从 Clash 回来：代理那一步就算做完了，刷新还等着你去点
            ChainStage.WAITING_X -> vpnDone = true
            ChainStage.DONE -> {
                vpnDone = true
                // 只有真的进过 X 才把「刷新确认」打勾，避免没走这一步也显示完成
                if (ChainWatcher.reachedX) checkDone = true
            }
            else -> Unit
        }

        when (pending) {
            PrepStep.VPN -> vpnDone = true
            PrepStep.CHECK -> checkDone = true
            null -> return
        }
        pending = null
    }

    fun resetPrep() {
        stopPrepare()
        vpnDone = false
        checkDone = false
        pending = null
    }

    // ---------- 发送 ----------

    fun sendToGpt(): ChatGptSender.Outcome {
        // 都要走了，别再盯着前台 —— 否则几分钟后会在 ChatGPT 里弹一条“准备中断”
        if (ChainWatcher.running) stopPrepare()

        val text = buildText()
        if (text.isBlank()) {
            toast = "这一组没有词，先换一组"
            return ChatGptSender.Outcome.FAILED
        }
        val outcome = ChatGptSender.send(context = appContext, text = text, targetPackage = config.gptPkg)
        val message = when (outcome) {
            ChatGptSender.Outcome.SHARED -> "已填进 ChatGPT 输入框，点一下发送键"
            ChatGptSender.Outcome.OPENED -> "ChatGPT 已打开，文本在剪贴板，长按输入框粘贴"
            ChatGptSender.Outcome.NOT_INSTALLED -> "没找到 ChatGPT，先到设置里指定一下"
            ChatGptSender.Outcome.FAILED -> "打开 ChatGPT 失败，文本已复制到剪贴板"
        }
        if (outcome == ChatGptSender.Outcome.SHARED || outcome == ChatGptSender.Outcome.OPENED) {
            // 马上就切到 ChatGPT 了，本页的 Snackbar 用户看不到，改用系统 Toast
            runCatching {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
            checkDone = true
        } else {
            toast = message
        }
        return outcome
    }

    fun copyText() {
        val text = buildText()
        if (text.isBlank()) return
        ChatGptSender.copy(appContext, text, "十词一组")
        toast = "已复制，去 ChatGPT 里粘贴"
    }

    /** 首次进入自动打开代理工具，界面画完再启动，避免和首帧抢前台 */
    fun autoOpenClashIfNeeded() {
        if (!config.autoOpenClash) return
        if (config.clashPkg.isBlank()) return
        Handler(Looper.getMainLooper()).postDelayed({ openClash() }, 600L)
    }

    /** 按当前设置决定要不要常驻通知保活；正在盯梢时不要打断 */
    fun syncKeepAlive() {
        if (ChainWatcher.running) return
        if (config.keepAlive) ChainService.idle(appContext) else ChainService.stop(appContext)
    }

    // ---------- 通用 ----------

    /** 打开内置单元：JSON 在 assets 里，读盘放到 IO 线程 */
    fun openUnit(id: Int) {
        readingUnitId = id
        val cached = currentUnit
        if (cached?.id == id) {
            screen = Screen.READER
            return
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                com.chatty.reader.study.UnitStore.load(appContext, id)
            }
            if (loaded != null) {
                currentUnit = loaded
                screen = Screen.READER
            } else {
                toast = "这个单元没读到，可能没打进包里"
            }
        }
    }

    fun dismissToast() {
        toast = null
    }

    fun dispose() {
        scope.cancel()
    }
}
