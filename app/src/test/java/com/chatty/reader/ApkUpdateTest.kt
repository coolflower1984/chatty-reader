package com.chatty.reader

import com.chatty.reader.study.ApkUpdate
import com.chatty.reader.study.UpdateRepo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动时该不该弹更新框。
 *
 * 这里最容易出两种错：一是同版本反复打扰，二是跳过了一个版本之后，
 * 下一个版本也不敢提醒了。
 */
class ApkUpdateTest {

    private fun m(code: Int, file: String = "chatty-reader-v4.4.apk") = UpdateRepo.Manifest(
        totalUnits = 240,
        builtIn = 10,
        packs = emptyList(),
        apkVersionCode = code,
        apkVersionName = "4.%d".format(code - 15),
        apkFile = file,
        apkBytes = 44_000_000L,
        apkNotes = ""
    )

    @Test
    fun promptsWhenServerIsNewer() {
        assertTrue(ApkUpdate.shouldPrompt(m(19), currentCode = 18, skippedCode = 0))
    }

    @Test
    fun silentOnSameVersion() {
        assertFalse(ApkUpdate.shouldPrompt(m(18), currentCode = 18, skippedCode = 0))
    }

    @Test
    fun silentOnOlderManifest() {
        assertFalse(ApkUpdate.shouldPrompt(m(17), currentCode = 18, skippedCode = 0))
    }

    @Test
    fun respectsSkipOfThatSameVersion() {
        assertFalse(ApkUpdate.shouldPrompt(m(19), currentCode = 18, skippedCode = 19))
    }

    @Test
    fun skipOnlyCoversThatVersion() {
        // 跳过了 19，出了 20 还是要提醒
        assertTrue(ApkUpdate.shouldPrompt(m(20), currentCode = 18, skippedCode = 19))
    }

    @Test
    fun noFileMeansNoPrompt() {
        assertFalse(ApkUpdate.shouldPrompt(m(19, file = ""), currentCode = 18, skippedCode = 0))
    }
}
