package com.chatty.reader

import com.chatty.reader.watch.ChainMachine
import com.chatty.reader.watch.ChainMachine.Action
import com.chatty.reader.watch.ChainMachine.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 准备阶段的状态机。
 *
 * 这些用例对应真实会发生的走法：在 Clash 里点开关、中途绕去桌面、
 * 没开 X 就回来、Clash 一直没起来、在 X 里待过头。
 */
class ChainMachineTest {

    private fun machine(
        clash: String = "com.clash",
        x: String = "com.x",
        dwellMs: Long = 5_000,
        clashDwellMs: Long = 3_000,
        clashTimeoutMs: Long = 20_000,
        maxTotalMs: Long = 5 * 60 * 1000
    ) = ChainMachine(
        selfPkg = "com.self",
        clashPkg = clash,
        xPkg = x,
        dwellMs = dwellMs,
        clashDwellMs = clashDwellMs,
        tickMs = 500,
        clashTimeoutMs = clashTimeoutMs,
        maxTotalMs = maxTotalMs
    )

    // ---------------------------------------------------------- Clash 段

    @Test
    fun staysQuietWhileStillOnOwnScreen() {
        val m = machine()
        repeat(3) { assertEquals(Action.NONE, m.onTick("com.self")) }
        assertEquals(Step.TO_CLASH, m.step)
    }

    @Test
    fun detectsClashInForeground() {
        val m = machine()
        m.onTick("com.self")
        m.onTick("com.clash")
        assertEquals(Step.AT_CLASH, m.step)
    }

    @Test
    fun countsDownInsideClash() {
        val m = machine(clashDwellMs = 3_000)
        m.onTick("com.clash")
        assertEquals(Step.AT_CLASH, m.step)
        // 刚进来显示满额，不会一进来就少一秒
        assertEquals(3, m.countdownSeconds)

        m.onTick("com.clash")
        assertEquals(2, m.countdownSeconds)
    }

    @Test
    fun leavesClashAfterDwell() {
        val m = machine(clashDwellMs = 3_000)
        // 前 5 拍（2.5 秒）还不到点
        repeat(5) { assertEquals(Action.NONE, m.onTick("com.clash")) }
        // 第 6 拍刚好 3 秒
        assertEquals(Action.LEAVE_CLASH, m.onTick("com.clash"))
        // 拉回来了，但流程没结束，还在等 X
        assertEquals(Step.WAITING_X, m.step)
        assertFalse(m.reachedX)
    }

    @Test
    fun doesNotPullAgainWhileStillInClash() {
        val m = machine(clashDwellMs = 3_000)
        repeat(6) { m.onTick("com.clash") }

        // 系统把界面切走需要一点时间，这几拍不该重复触发
        repeat(4) { assertEquals(Action.NONE, m.onTick("com.clash")) }
        assertEquals(Step.WAITING_X, m.step)
    }

    @Test
    fun leavingClashMidwayResetsTheCounter() {
        val m = machine(clashDwellMs = 3_000)
        repeat(4) { m.onTick("com.clash") }   // 已待 2 秒
        m.onTick("com.wechat")                // 手滑切走，计时作废
        m.onTick("com.clash")                 // 回来重新数

        repeat(4) { assertEquals(Action.NONE, m.onTick("com.clash")) }
        // 重新数满 3 秒才走
        assertEquals(Action.LEAVE_CLASH, m.onTick("com.clash"))
    }

    @Test
    fun comingBackFromClashGoesToWaitingX() {
        val m = machine(clashDwellMs = 3_000)
        repeat(6) { m.onTick("com.clash") }
        m.onTick("com.clash")   // 界面还没切走的空档

        assertEquals(Action.CLASH_BACK, m.onTick("com.self"))
        assertEquals(Step.WAITING_X, m.step)
        // 只提示一次，之后安静等着
        assertEquals(Action.NONE, m.onTick("com.self"))
        assertFalse(m.reachedX)
    }

    @Test
    fun comingBackEarlyFromClashAlsoCountsAsDone() {
        val m = machine(clashDwellMs = 3_000)
        m.onTick("com.clash")
        assertEquals(Action.CLASH_BACK, m.onTick("com.self"))
        assertEquals(Step.WAITING_X, m.step)
    }

    // ---------------------------------------------------------- X 段

    @Test
    fun countsDownWhileInX() {
        val m = machine(dwellMs = 5_000)
        m.onTick("com.clash")
        m.onTick("com.x")

        assertEquals(Step.IN_X, m.step)
        assertEquals(5, m.countdownSeconds)

        m.onTick("com.x")
        assertEquals(4, m.countdownSeconds)
    }

    @Test
    fun asksToComeBackAfterDwell() {
        val m = machine(dwellMs = 5_000)
        m.onTick("com.clash")

        // 前 9 拍（4.5 秒）都还在等
        repeat(9) { assertEquals(Action.NONE, m.onTick("com.x")) }
        // 第 10 拍刚好到 5 秒
        assertEquals(Action.BRING_BACK, m.onTick("com.x"))
        assertEquals(Step.DONE, m.step)
        assertTrue(m.reachedX)
    }

    @Test
    fun userComingBackFirstWins() {
        val m = machine(dwellMs = 20_000)
        m.onTick("com.clash")
        m.onTick("com.x")
        m.onTick("com.x")

        assertEquals(Action.ARRIVED_BACK, m.onTick("com.self"))
        assertEquals(Step.DONE, m.step)
        assertTrue(m.reachedX)
    }

    @Test
    fun fullChainFromClashToXAndBack() {
        val m = machine(dwellMs = 5_000, clashDwellMs = 2_000)

        // Clash 待 2 秒
        repeat(3) { assertEquals(Action.NONE, m.onTick("com.clash")) }
        assertEquals(Action.LEAVE_CLASH, m.onTick("com.clash"))

        // 回到本页，界面提示去开 X
        assertEquals(Action.CLASH_BACK, m.onTick("com.self"))
        assertEquals(Step.WAITING_X, m.step)

        // 打开 X，待 5 秒
        repeat(9) { assertEquals(Action.NONE, m.onTick("com.x")) }
        assertEquals(Action.BRING_BACK, m.onTick("com.x"))
        assertEquals(Step.DONE, m.step)
        assertTrue(m.reachedX)
    }

    // ---------------------------------------------------------- 绕路

    @Test
    fun passingThroughHomeScreenIsFine() {
        val m = machine()
        m.onTick("com.clash")
        m.onTick("com.android.launcher")

        assertEquals(Step.WAITING_X, m.step)

        m.onTick("com.x")
        assertEquals(Step.IN_X, m.step)
    }

    @Test
    fun leavingXResetsTheCountdown() {
        val m = machine(dwellMs = 5_000)
        m.onTick("com.clash")
        repeat(4) { m.onTick("com.x") }   // 已待 2 秒
        m.onTick("com.wechat")            // 走开，计时作废

        repeat(9) { assertEquals(Action.NONE, m.onTick("com.x")) }
        // 重新数满 5 秒才回来，而不是接着之前的
        assertEquals(Action.BRING_BACK, m.onTick("com.x"))
    }

    @Test
    fun skippingXIsNotMarkedAsRefreshed() {
        val m = machine()
        m.onTick("com.clash")
        assertEquals(Action.CLASH_BACK, m.onTick("com.self"))

        // 没进过 X，界面不该把“刷新完成”打勾
        assertFalse(m.reachedX)
    }

    // ---------------------------------------------------------- 失败

    @Test
    fun failsWhenClashNeverAppears() {
        val m = machine(clashTimeoutMs = 3_000)

        var action = Action.NONE
        // 每拍 500ms，第 7 拍是 3.5 秒，越过 3 秒阈值
        for (i in 0 until 10) {
            action = m.onTick("com.self")
            if (action != Action.NONE) break
        }
        assertEquals(Action.FAIL_CLASH_TIMEOUT, action)
        assertEquals(Step.FAILED, m.step)
    }

    @Test
    fun failsWhenItDragsTooLong() {
        // 停留阈值调得比总时长还大，就会一直卡在 Clash 直到超时
        val m = machine(clashDwellMs = 60_000, maxTotalMs = 4_000)
        m.onTick("com.clash")

        var action = Action.NONE
        for (i in 0 until 20) {
            action = m.onTick("com.clash")
            if (action != Action.NONE) break
        }
        assertEquals(Action.FAIL_MAX_WAIT, action)
        assertEquals(Step.FAILED, m.step)
    }

    @Test
    fun unknownForegroundDoesNotCrash() {
        val m = machine()
        assertEquals(Action.NONE, m.onTick(null))
        assertEquals(Step.TO_CLASH, m.step)
    }

    // ---------------------------------------------------------- 没配 X

    @Test
    fun blankXPackageMeansNothingIsCounted() {
        val m = machine(x = "")
        m.onTick("com.clash")
        repeat(20) { assertEquals(Action.NONE, m.onTick("com.x")) }
        assertFalse(m.reachedX)
    }
}
