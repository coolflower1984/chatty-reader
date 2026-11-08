package com.chatty.reader.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 把选中的词或句子递给 DeepSeek 求详解。
 *
 * 走系统分享，不经过任何服务器、不需要 API Key：DeepSeek 安卓版注册了 text/plain
 * 接收方，文字会直接落在它的输入框里，发送键还得自己按一下。
 * 没装 DeepSeek 就退到系统选择器，用户选别的 AI 应用也能用。
 */
object AskAi {

    /** DeepSeek 官方安卓包名 */
    const val DEEPSEEK_PKG = "com.deepseek.chat"

    enum class Outcome { SENT, CHOOSER, FAILED }

    fun prompt(text: String, kind: String, unitId: Int, context: String): String {
        val what = if (kind == "sentence") "句子" else "单词"
        val source = if (unitId > 0) "Unit $unitId" else "课外材料"
        return buildString {
            appendLine("我在读英语材料，遇到一个不认识的$what，帮我讲清楚。")
            appendLine()
            appendLine("「$text」")
            appendLine()
            appendLine("出处：$source")
            if (context.isNotBlank()) {
                appendLine("原段落：$context")
                appendLine()
            }
            appendLine("请用中文回答，按这个顺序：")
            if (kind == "sentence") {
                appendLine("1. 先给这句话的自然中文翻译")
                appendLine("2. 拆句子结构：主句、从句、修饰成分分别在哪儿")
                appendLine("3. 指出里面最容易看错的语法点或固定搭配，各举一个例子")
                appendLine("4. 用一句话告诉我以后再遇到这类句子该怎么断句")
            } else {
                appendLine("1. 音标、词性、在这句话里的确切意思")
                appendLine("2. 常见搭配或短语，附中文")
                appendLine("3. 一个六级难度的例句，带翻译")
                appendLine("4. 用一句话给我一个记忆点")
            }
        }.trim()
    }

    fun send(context: Context, text: String, kind: String, unitId: Int, contextText: String): Outcome {
        val body = prompt(text.trim(), kind, unitId, contextText)
        // 先落一份在剪贴板：分享失败也不至于白选一次
        ChatGptSender.copy(context, body, "十词一组")

        val pm = context.packageManager
        val installed = runCatching {
            pm.getApplicationInfo(DEEPSEEK_PKG, 0)
        }.isSuccess

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (installed) {
            share.setPackage(DEEPSEEK_PKG)
            val resolved = runCatching {
                pm.resolveActivity(share, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull()
            if (resolved != null && runCatching { context.startActivity(share) }.isSuccess) {
                return Outcome.SENT
            }
            // 定向分享被拒（部分 ROM 限制），退回启动 App，文字已在剪贴板里
            val launch = runCatching { pm.getLaunchIntentForPackage(DEEPSEEK_PKG) }.getOrNull()
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(launch) }.isSuccess) return Outcome.CHOOSER
            }
        }

        val chooser = Intent.createChooser(share, "用哪个 AI 详解").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (runCatching { context.startActivity(chooser) }.isSuccess) {
            Outcome.CHOOSER
        } else {
            Outcome.FAILED
        }
    }
}
