package com.chatty.reader.watch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 一次「准备」进行到哪一步 */
enum class ChainStage {
    /** 没在跑 */
    IDLE,
    /** 刚打开 Clash，还没看到它 */
    TO_CLASH,
    /** Clash 在前台，等你自己点开关 */
    AT_CLASH,
    /** 见过 Clash，在等 X 出现 */
    WAITING_X,
    /** X 在前台，倒计时中 */
    IN_X,
    /** 已经把你送回来了 */
    DONE,
    /** 超时或缺少权限 */
    FAILED
}

/**
 * 监听进度。服务写、界面读，跨进程内共享。
 *
 * 只做一件事：告诉界面“现在走到哪了、还有几秒回来”。
 */
object ChainWatcher {

    var stage by mutableStateOf(ChainStage.IDLE)
    var message by mutableStateOf("")
    /** IN_X 阶段剩余的秒数 */
    var countdown by mutableIntStateOf(0)
    /** 这一轮有没有真的进过 X —— 没进过就不该把“刷新完成”打勾 */
    var reachedX by mutableStateOf(false)

    /** 正在跑（含刚结束、等用户看一眼的状态） */
    val running: Boolean
        get() = stage == ChainStage.TO_CLASH ||
            stage == ChainStage.AT_CLASH ||
            stage == ChainStage.WAITING_X ||
            stage == ChainStage.IN_X

    fun reset() {
        stage = ChainStage.IDLE
        message = ""
        countdown = 0
        reachedX = false
    }

    fun begin(message: String) {
        stage = ChainStage.TO_CLASH
        this.message = message
        countdown = 0
        reachedX = false
    }

    fun moveTo(stage: ChainStage, message: String, countdown: Int = 0) {
        this.stage = stage
        this.message = message
        this.countdown = countdown
    }
}
