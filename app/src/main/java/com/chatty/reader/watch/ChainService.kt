package com.chatty.reader.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.chatty.reader.MainActivity

/**
 * 「准备阶段」的观察者。
 *
 * 不做任何点击 —— 开关、打开 X、下拉刷新全部由你亲手完成。
 * 它只做一件事：盯着哪个应用到了前台，
 * 你在 Clash 里待够几秒就把你送回来，你在 X 里刷完再把你送回来。
 *
 * 之所以需要它：Android 10 起，应用在后台不能自己跳到前台。
 * 你在 X 里的时候本应用在后台，没有这层观察，你只能自己从最近任务切回来。
 *
 * 同时兼任“待命”状态：没在观察时，这条低优先级通知能把进程留住，
 * 免得刚切出去就被系统清掉、回来又要重来一遍。
 *
 * 判断逻辑在 [ChainMachine] 里，本类只负责三件事：查前台、发通知、切界面。
 */
class ChainService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private var watching = false
    private var machine: ChainMachine? = null

    private var clashPkg = ""
    private var xPkg = ""
    private var dwellMs = DEFAULT_DWELL_MS
    private var clashDwellMs = ChainMachine.DEFAULT_CLASH_DWELL_MS

    /**
     * 这台机器能不能把进度显示到状态栏（小米焦点通知 / 超级岛）。
     * 探测要跨进程查一次，所以不放在发通知的热路径上，只在每轮开始时查一次。
     * 不能用 lazy —— 保活的服务会一直活着，用户在系统设置里刚打开权限时，
     * 缓存下来的 false 就再也翻不了身。
     */
    private var islandReady = false

    /** 用户手动要求跳过探测，无条件带岛参数 */
    private var islandForce = false

    /** 上一次推到通知栏的秒数，用来避免同一秒重复 notify */
    private var lastPushedCountdown = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        // 5 秒内必须进前台，先挂一条通用通知，onStartCommand 再改成具体文案
        runCatching { startForeground(NOTIFICATION_ID, buildNotification(IDLE_TITLE, IDLE_TEXT, false)) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WATCH -> {
                clashPkg = intent.getStringExtra(EXTRA_CLASH).orEmpty()
                xPkg = intent.getStringExtra(EXTRA_X).orEmpty()
                dwellMs = intent.getLongExtra(EXTRA_DWELL, DEFAULT_DWELL_MS)
                    .coerceIn(MIN_DWELL_MS, MAX_DWELL_MS)
                clashDwellMs = intent.getLongExtra(EXTRA_CLASH_DWELL, ChainMachine.DEFAULT_CLASH_DWELL_MS)
                    .coerceIn(MIN_CLASH_DWELL_MS, MAX_CLASH_DWELL_MS)
                startWatching()
            }

            else -> {
                stopWatching()
                push(IDLE_TITLE, IDLE_TEXT, false)
            }
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- 监听

    private fun startWatching() {
        handler.removeCallbacksAndMessages(null)
        watching = true
        lastPushedCountdown = -1
        // 每轮重新探一次：你可能刚在系统设置里把这个开关打开
        islandForce = islandForceEnabled()
        islandReady = islandForce || runCatching { FocusIsland.available(this) }.getOrDefault(false)
        machine = ChainMachine(
            selfPkg = packageName,
            clashPkg = clashPkg,
            xPkg = xPkg,
            dwellMs = dwellMs,
            clashDwellMs = clashDwellMs
        )

        ChainWatcher.begin("正在打开代理工具")
        push("正在打开代理工具", "点开关，待 ${clashDwellMs / 1000} 秒后自动回来", false, onIsland = true)
        handler.postDelayed(tick, TICK_MS)
    }

    private fun stopWatching() {
        watching = false
        handler.removeCallbacksAndMessages(null)
    }

    private val tick = object : Runnable {
        override fun run() {
            val m = machine ?: return
            if (!watching) return

            val before = m.step
            val action = m.onTick(UsageProbe.foreground(this@ChainService))

            ChainWatcher.reachedX = m.reachedX
            ChainWatcher.countdown = m.countdownSeconds

            if (m.step != before) {
                announce(m.step, m.countdownSeconds)
            } else if (m.step == ChainMachine.Step.AT_CLASH || m.step == ChainMachine.Step.IN_X) {
                // 倒计时每过半秒就要刷一次，不然界面上那个数字是死的
                ChainWatcher.moveTo(
                    stageOf(m.step),
                    messageFor(m.step, m.countdownSeconds),
                    m.countdownSeconds
                )
                // 岛和状态栏上的秒数也得跟着走，否则那片区域一直停在初见时的数字
                if (islandReady && m.countdownSeconds != lastPushedCountdown) {
                    lastPushedCountdown = m.countdownSeconds
                    push(
                        islandTitle(m.step),
                        messageFor(m.step, m.countdownSeconds),
                        false,
                        onIsland = true
                    )
                }
            }

            when (action) {
                ChainMachine.Action.LEAVE_CLASH -> {
                    leaveClash()
                    return
                }

                ChainMachine.Action.CLASH_BACK -> {
                    clashBack()
                    return
                }

                ChainMachine.Action.ARRIVED_BACK -> {
                    arriveBack()
                    return
                }

                ChainMachine.Action.BRING_BACK -> {
                    bringBack()
                    return
                }

                ChainMachine.Action.FAIL_CLASH_TIMEOUT -> {
                    fail("没能打开代理工具，检查一下设置里选的包名")
                    return
                }

                ChainMachine.Action.FAIL_MAX_WAIT -> {
                    fail("等太久了，已停止观察。可以再点一次「开始准备」")
                    return
                }

                ChainMachine.Action.NONE -> handler.postDelayed(this, TICK_MS)
            }
        }
    }

    private fun stageOf(step: ChainMachine.Step): ChainStage = when (step) {
        ChainMachine.Step.TO_CLASH -> ChainStage.TO_CLASH
        ChainMachine.Step.AT_CLASH -> ChainStage.AT_CLASH
        ChainMachine.Step.WAITING_X -> ChainStage.WAITING_X
        ChainMachine.Step.IN_X -> ChainStage.IN_X
        ChainMachine.Step.DONE -> ChainStage.DONE
        ChainMachine.Step.FAILED -> ChainStage.FAILED
    }

    private fun messageFor(step: ChainMachine.Step, countdown: Int): String = when (step) {
        ChainMachine.Step.TO_CLASH -> "正在打开代理工具"
        ChainMachine.Step.AT_CLASH -> "$countdown 秒后自动回到本页"
        ChainMachine.Step.WAITING_X -> "点「打开 X」刷一下，刷完自动回来"
        ChainMachine.Step.IN_X -> "$countdown 秒后自动回来"
        ChainMachine.Step.DONE -> "已回到本页"
        ChainMachine.Step.FAILED -> "已停止观察"
    }

    /** 岛/状态栏上那行标题，比通知标题更短 */
    private fun islandTitle(step: ChainMachine.Step): String = when (step) {
        ChainMachine.Step.AT_CLASH -> "点 Clash 的开关"
        ChainMachine.Step.IN_X -> "在 X 里刷新"
        ChainMachine.Step.WAITING_X -> "代理已开好"
        else -> "正在准备"
    }

    /** 阶段变化时同步界面与通知（只在真正变的时候调，避免通知反复弹） */
    private fun announce(step: ChainMachine.Step, countdown: Int) {
        when (step) {
            ChainMachine.Step.AT_CLASH -> {
                ChainWatcher.moveTo(
                    ChainStage.AT_CLASH,
                    messageFor(step, countdown),
                    countdown
                )
                lastPushedCountdown = countdown
                push(islandTitle(step), messageFor(step, countdown), false, onIsland = true)
            }

            ChainMachine.Step.WAITING_X -> {
                ChainWatcher.moveTo(ChainStage.WAITING_X, messageFor(step, 0))
                push(islandTitle(step), messageFor(step, 0), false, onIsland = true)
            }

            ChainMachine.Step.IN_X -> {
                ChainWatcher.moveTo(
                    ChainStage.IN_X,
                    messageFor(step, countdown),
                    countdown
                )
                lastPushedCountdown = countdown
                push(islandTitle(step), messageFor(step, countdown), false, onIsland = true)
            }

            ChainMachine.Step.TO_CLASH ->
                ChainWatcher.moveTo(ChainStage.TO_CLASH, "正在打开代理工具")

            else -> Unit
        }
    }

    /** 在 Clash 里待够了：拉回本页，但监听不停，接下来等你点「打开 X」 */
    private fun leaveClash() {
        ChainWatcher.moveTo(ChainStage.WAITING_X, "已开好代理，正在回到本页…", 0)

        if (!jumpToSelf()) {
            // 跳不过去也别停，靠通知兜底
            push("点一下回到十词一组", "代理已开启，点这里回来再打开 X", true)
            handler.postDelayed(tick, TICK_MS)
            return
        }

        push("代理已开启", "回来点「打开 X」刷一下", false, onIsland = true)
        handler.postDelayed(tick, TICK_MS)
    }

    /** 从 Clash 回来后，把界面切到「等你打开 X」 */
    private fun clashBack() {
        ChainWatcher.moveTo(ChainStage.WAITING_X, "代理已开好，点下面的「打开 X」刷一下", 0)
        push("代理已开好", "点「打开 X」刷一下，刷完自动回来", false, onIsland = true)
        handler.postDelayed(tick, TICK_MS)
    }

    /** 已经回到本应用（用户自己切回，或我们拉回成功） */
    private fun arriveBack() {
        stopWatching()
        ChainWatcher.moveTo(ChainStage.DONE, doneMessage())
        settleDone()
    }

    /** 在 X 里待够了，把自己拉回前台 */
    private fun bringBack() {
        stopWatching()
        ChainWatcher.moveTo(ChainStage.DONE, "正在回到十词一组…")

        if (!jumpToSelf()) {
            fail("系统不允许自动切回，点这条通知回到十词一组")
            return
        }

        // 部分 ROM 会静默拦下这次跳转：1.2 秒后复核，没回来就把通知改成明确入口
        handler.postDelayed({ afterBringBack() }, 1200)
    }

    /** 把自己拉到前台。从服务里发起，必须带 NEW_TASK。 */
    private fun jumpToSelf(): Boolean = runCatching {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(MainActivity.EXTRA_BROUGHT_BACK, true)
            }
        )
    }.isSuccess

    private fun afterBringBack() {
        if (UsageProbe.foreground(this) == packageName) {
            ChainWatcher.moveTo(ChainStage.DONE, doneMessage())
            settleDone()
        } else {
            ChainWatcher.moveTo(ChainStage.DONE, "已备好。系统没让我切回来，点通知回到本页")
            push("点一下回到十词一组", "十个词已备好，点这里就能发送", true)
            handOff()
        }
    }

    private fun doneMessage(): String =
        if (ChainWatcher.reachedX) "准备好了，点下面的「发送到 ChatGPT」"
        else "已回到本页。没见你打开 X，确认网络没问题就直接发送"

    private fun fail(reason: String) {
        stopWatching()
        ChainWatcher.moveTo(ChainStage.FAILED, reason)
        push("准备中断", reason, true)
        handOff()
    }

    /** 走完之后：开着保活就转“待命中”，否则撤掉前台身份 */
    private fun settleDone() {
        if (keepAliveEnabled()) {
            push(IDLE_TITLE, IDLE_TEXT, false)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** 需要保留那条可点击的通知时用：撤掉前台身份但把通知留下 */
    private fun handOff() {
        if (keepAliveEnabled()) {
            // 继续以“待命中”挂着，通知已经是最终文案
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    // ---------------------------------------------------------------- 通知

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "准备阶段",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在你手动操作 Clash 和 X 时，负责把你送回来"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        // 焦点通知/超级岛不吃 IMPORTANCE_LOW 的渠道，得单开一个。
        // 静音、不震动、不亮灯 —— 它出现在屏幕顶部但不该打扰你。
        val island = NotificationChannel(
            CHANNEL_ID_ISLAND,
            "准备阶段 · 状态栏",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "支持的小米机型上，把准备进度显示在状态栏和超级岛"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(island)
    }

    private fun buildNotification(
        title: String,
        text: String,
        urgent: Boolean,
        onIsland: Boolean = false
    ): Notification {
        // REORDER_TO_FRONT + SINGLE_TOP：切回已经在后台的界面，而不是重开一个。
        // 岛上点空白处、点通知、点「回到本页」按钮，走的都是这一个 PendingIntent。
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // urgent 是要你点一下的提醒，最需要被看见，不上岛，走普通渠道
        val useIsland = onIsland && islandReady && !urgent

        val notification = Notification.Builder(
            this,
            if (useIsland) CHANNEL_ID_ISLAND else CHANNEL_ID
        )
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pending)
            .setOngoing(!urgent)
            .setOnlyAlertOnce(true)
            .setTicker(if (useIsland) text else null)
            .build()

        if (useIsland) {
            FocusIsland.decorate(this, notification, title, text, pending, force = islandForce)
        }
        return notification
    }

    private fun push(title: String, text: String, urgent: Boolean, onIsland: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            nm.notify(NOTIFICATION_ID, buildNotification(title, text, urgent, onIsland))
        }
    }

    private fun keepAliveEnabled(): Boolean = runCatching {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_ALIVE, true)
    }.getOrDefault(true)

    private fun islandForceEnabled(): Boolean = runCatching {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ISLAND_FORCE, false)
    }.getOrDefault(false)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "prepare_chain"
        private const val CHANNEL_ID_ISLAND = "prepare_chain_island"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_WATCH = "com.chatty.reader.WATCH"
        private const val EXTRA_CLASH = "clash_pkg"
        private const val EXTRA_X = "x_pkg"
        private const val EXTRA_DWELL = "dwell_ms"
        private const val EXTRA_CLASH_DWELL = "clash_dwell_ms"

        private const val PREFS = "chatty_prefs"
        private const val KEY_KEEP_ALIVE = "keep_alive_pref"
        private const val KEY_ISLAND_FORCE = "island_force"

        private const val TICK_MS = 500L

        private const val DEFAULT_DWELL_MS = 10_000L
        const val MIN_DWELL_MS = 3_000L
        const val MAX_DWELL_MS = 30_000L

        const val MIN_CLASH_DWELL_MS = 1_000L
        const val MAX_CLASH_DWELL_MS = 15_000L

        private const val IDLE_TITLE = "十词一组 · 待命中"
        private const val IDLE_TEXT = "点「开始准备」后会替你盯着，刷完 X 自动回来"

        /** 开始一次盯梢 */
        fun watch(
            context: Context,
            clashPkg: String,
            xPkg: String,
            dwellMs: Long,
            clashDwellMs: Long = ChainMachine.DEFAULT_CLASH_DWELL_MS
        ) {
            val intent = Intent(context, ChainService::class.java).apply {
                action = ACTION_WATCH
                putExtra(EXTRA_CLASH, clashPkg)
                putExtra(EXTRA_X, xPkg)
                putExtra(EXTRA_DWELL, dwellMs)
                putExtra(EXTRA_CLASH_DWELL, clashDwellMs)
            }
            runCatching { context.startForegroundService(intent) }
        }

        /** 只保活，不观察 */
        fun idle(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ChainService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ChainService::class.java)) }
        }
    }
}
