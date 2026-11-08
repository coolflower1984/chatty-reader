package com.chatty.reader.watch

/**
 * 「准备阶段」的状态机，不碰任何 Android API，便于单测。
 *
 * 输入只有一个：这一拍前台是哪个应用。输出只有一个：接下来该做什么。
 * 服务只负责把这两头接起来（查前台、发通知、拉回界面）。
 *
 * 一轮完整走法：
 *   开始准备 → 打开代理 → 你在 Clash 里点开关（待够 [clashDwellMs]）→ 自动回本应用
 *   → 你点「打开 X」→ 在 X 里下拉刷新（待够 [dwellMs]）→ 自动回本应用 → 发送
 *
 * 两段停留分开计时：Clash 那段短，只够点一下开关；X 那段长，够你拉一次扫两眼。
 */
class ChainMachine(
    private val selfPkg: String,
    private val clashPkg: String,
    private val xPkg: String,
    private val dwellMs: Long,
    private val clashDwellMs: Long = DEFAULT_CLASH_DWELL_MS,
    private val tickMs: Long = 500L,
    private val clashTimeoutMs: Long = 20_000L,
    private val maxTotalMs: Long = 5 * 60 * 1000L
) {

    enum class Step { TO_CLASH, AT_CLASH, WAITING_X, IN_X, DONE, FAILED }

    enum class Action {
        NONE,

        /** 在 Clash 里待够了，把界面拉回本应用；但流程还没完，接下来等 X */
        LEAVE_CLASH,

        /** 已经回到本应用了，可以提示「打开 X」 */
        CLASH_BACK,

        /** 在 X 里待够了，把界面拉回本应用；这一轮结束 */
        BRING_BACK,

        /** 你自己切回来了 */
        ARRIVED_BACK,

        FAIL_CLASH_TIMEOUT,
        FAIL_MAX_WAIT
    }

    var step = Step.TO_CLASH
        private set

    /** 这一轮是否真的进过 X */
    var reachedX = false
        private set

    /** 当前阶段的剩余秒数，供界面显示；不在倒计时阶段时为 0 */
    var countdownSeconds = 0
        private set

    private var sawClash = false

    /** 已经因为「Clash 待够」请求过回跳，避免紧接着的几拍重复拉一次 */
    private var clashSettled = false

    /** 「已回到本应用」只提示一次 */
    private var clashBackAnnounced = false

    private var clashTicks = 0
    private var xTicks = 0
    private var totalTicks = 0

    fun onTick(foreground: String?): Action {
        totalTicks++

        if (totalTicks * tickMs > maxTotalMs) {
            step = Step.FAILED
            return Action.FAIL_MAX_WAIT
        }

        // 1. 本应用在前台
        if (foreground == selfPkg) {
            if (step == Step.TO_CLASH) {
                // 刚发出启动指令时自己还在前台，属正常，等 Clash 起来
                return if (totalTicks * tickMs > clashTimeoutMs) {
                    step = Step.FAILED
                    Action.FAIL_CLASH_TIMEOUT
                } else {
                    Action.NONE
                }
            }

            // 进过 X 再回来：整条流程走完
            if (reachedX) {
                step = Step.DONE
                countdownSeconds = 0
                return Action.ARRIVED_BACK
            }

            // 只在 Clash 待过、还没去 X：这里是中转站，不能当成结束
            if (sawClash) {
                step = Step.WAITING_X
                countdownSeconds = 0
                // 中途离开过 Clash，再回去要重新数满
                if (!clashSettled) clashTicks = 0
                return if (clashBackAnnounced) {
                    Action.NONE
                } else {
                    clashBackAnnounced = true
                    Action.CLASH_BACK
                }
            }

            step = Step.DONE
            countdownSeconds = 0
            return Action.ARRIVED_BACK
        }

        // 2. 停在 Clash：点开关的时间就在这段里
        if (clashPkg.isNotBlank() && foreground == clashPkg) {
            sawClash = true
            xTicks = 0
            // 已经拉过一次了，等系统把界面切走，别再重复计时
            if (clashSettled) return Action.NONE

            step = Step.AT_CLASH
            clashTicks++
            countdownSeconds = ceilSeconds(clashDwellMs - clashTicks * tickMs)
            return if (clashTicks * tickMs >= clashDwellMs) {
                clashSettled = true
                step = Step.WAITING_X
                countdownSeconds = 0
                Action.LEAVE_CLASH
            } else {
                Action.NONE
            }
        }

        // 3. 停在 X：下拉刷新之后停多久，由这里数
        if (xPkg.isNotBlank() && foreground == xPkg) {
            sawClash = true
            reachedX = true
            xTicks++
            // 向上取整：刚进 X 时显示满额秒数，不会一进来就少一秒
            countdownSeconds = ceilSeconds(dwellMs - xTicks * tickMs)
            step = Step.IN_X
            return if (xTicks * tickMs >= dwellMs) {
                step = Step.DONE
                countdownSeconds = 0
                Action.BRING_BACK
            } else {
                Action.NONE
            }
        }

        // 4. 桌面或别的应用：只在你已经见过 Clash 之后才提示「等你开 X」
        xTicks = 0
        if (!clashSettled) clashTicks = 0
        if (sawClash) step = Step.WAITING_X
        return Action.NONE
    }

    private fun ceilSeconds(remainMs: Long): Int =
        ((remainMs + tickMs * 2 - 1) / 1000).coerceAtLeast(0).toInt()

    companion object {
        /** 默认在代理工具里待 3 秒就拉回来：够点一下开关 */
        const val DEFAULT_CLASH_DWELL_MS = 3_000L
    }
}
