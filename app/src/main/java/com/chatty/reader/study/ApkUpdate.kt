package com.chatty.reader.study

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 「App 自身要不要更新」的判断与动作。
 *
 * 网络请求不在这里 —— 清单还是 UpdateCenter 拉一次，教材页和启动提示共用同一份。
 * 这里只负责记用户跳过了哪个版本、以及把下载页打开。
 */
object ApkUpdate {

    private const val PREFS = "chatty_prefs"
    private const val K_SKIP = "apk_skip_version"

    /**
     * 本次启动已经问过用户了（选了「以后再说」）。
     * 放在 object 里而不是 Compose 状态里：转屏、重建 Activity 不该再弹一次；
     * 进程被杀之后自然归零，下次打开又会问。
     */
    @Volatile
    var askedThisRun: Boolean = false

    fun skippedVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(K_SKIP, 0)

    fun skipVersion(context: Context, code: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(K_SKIP, code).apply()
    }

    /**
     * 清单里的版本比手上这个新，而且用户没说过「这个版本别烦我」。
     * 跳过的只在同一版本生效：下次真出了更新还得提醒。
     */
    fun shouldPrompt(context: Context, m: UpdateRepo.Manifest, currentCode: Int): Boolean =
        shouldPrompt(m, currentCode, skippedVersion(context))

    /** 纯判断，不碰 Context —— 单测直接测这个 */
    fun shouldPrompt(m: UpdateRepo.Manifest, currentCode: Int, skippedCode: Int): Boolean =
        m.apkVersionCode > currentCode &&
            m.apkFile.isNotBlank() &&
            skippedCode < m.apkVersionCode

    /** 交给浏览器下载。返回 false 表示这台机器上没有能接活的浏览器 */
    fun openDownload(context: Context, file: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(UpdateRepo.apkUrl(file)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    fun sizeText(bytes: Long): String =
        if (bytes <= 0) "" else "%.1f MB".format(bytes / 1024f / 1024f)
}
