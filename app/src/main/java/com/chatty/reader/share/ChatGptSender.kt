package com.chatty.reader.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 把文本送进 ChatGPT。
 *
 * 走的是系统分享：ChatGPT 安卓版注册了 text/plain 的接收方，
 * 分享过去的文字会直接落在它的输入框里，不经过任何第三方服务器，也不需要 API Key。
 * 发送按钮仍需手动点一下 —— ChatGPT 不对外开放“替我发送”的接口。
 */
object ChatGptSender {

    const val DEFAULT_PACKAGE = "com.openai.chatgpt"

    enum class Outcome {
        /** 文本已交给 ChatGPT，停在输入框 */
        SHARED,
        /** 只打开了 ChatGPT，文本在剪贴板，需要手动粘贴 */
        OPENED,
        NOT_INSTALLED,
        FAILED
    }

    fun copy(context: Context, text: String, label: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { cm.setPrimaryClip(ClipData.newPlainText(label, text)) }
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        pkg.isNotBlank() && runCatching {
            context.packageManager.getApplicationInfo(pkg, 0)
        }.isSuccess

    fun send(context: Context, text: String, targetPackage: String): Outcome {
        val pkg = targetPackage.ifBlank { DEFAULT_PACKAGE }

        // 先复制一份：分享路径万一失效，用户长按粘贴即可，不至于卡住
        copy(context, text, "十词一组")

        if (!isInstalled(context, pkg)) return Outcome.NOT_INSTALLED

        val pm = context.packageManager

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "CET-6 · 10 words")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val handler = runCatching {
            pm.resolveActivity(share, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()

        if (handler != null && runCatching { context.startActivity(share) }.isSuccess) {
            return Outcome.SHARED
        }

        val launch = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
            ?: return Outcome.FAILED
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(launch) }.isSuccess) {
            Outcome.OPENED
        } else {
            Outcome.FAILED
        }
    }
}
