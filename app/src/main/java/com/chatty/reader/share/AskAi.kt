package com.chatty.reader.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 把选中的词或句子递给 AI 求详解。
 *
 * 走系统分享，不经过任何服务器、不需要 API Key：元宝安卓版注册了 text/plain
 * 接收方，文字会直接落在它的输入框里，发送键还得自己按一下。
 * 目标应用可以在设置里换（默认元宝）；留空或没装就退到系统选择器。
 */
object AskAi {

    /** 腾讯元宝官方安卓包名（应用商店与各家下载站一致），设置里的默认值 */
    const val YUANBAO_PKG = "com.tencent.hunyuan.app.chat"

    enum class Outcome { SENT, CHOOSER, FAILED }

    /**
     * 默认提示词模板。可用的占位符：
     * `{kind}` 单词 / 句子、`{text}` 选中的原文、`{source}` 出处、
     * `{context}` 原段落（没有就是空）、`{steps}` 该类型对应的讲解步骤。
     *
     * 用户可以整段改写，只要把 `{text}` 留着，选中的字就不会丢；
     * 真删掉了也会自动补在末尾。
     */
    val DEFAULT_TEMPLATE = """
        我在读英语材料，遇到一个不认识的{kind}，帮我讲清楚。

        「{text}」

        出处：{source}
        {context}
        请用中文回答，按这个顺序：
        {steps}
    """.trimIndent()

    /** 默认的讲解步骤，按类型分开；模板里写 `{steps}` 就会换成其中一段 */
    fun stepsFor(kind: String): String = if (kind == "sentence") {
        """
            1. 先给这句话的自然中文翻译
            2. 拆句子结构：主句、从句、修饰成分分别在哪儿
            3. 指出里面最容易看错的语法点或固定搭配，各举一个例子
            4. 用一句话告诉我以后再遇到这类句子该怎么断句
        """.trimIndent()
    } else {
        """
            1. 音标、词性、在这句话里的确切意思
            2. 常见搭配或短语，附中文
            3. 一个六级难度的例句，带翻译
            4. 用一句话给我一个记忆点
        """.trimIndent()
    }

    /** 用默认模板拼一段 */
    fun prompt(text: String, kind: String, unitId: Int, context: String): String =
        prompt(DEFAULT_TEMPLATE, text, kind, unitId, context)

    /** 用指定模板拼一段。模板为空就退回默认，免得把用户的话发成空白 */
    fun prompt(
        template: String,
        text: String,
        kind: String,
        unitId: Int,
        context: String
    ): String {
        val tpl = template.ifBlank { DEFAULT_TEMPLATE }
        val what = if (kind == "sentence") "句子" else "单词"
        val source = if (unitId > 0) "Unit $unitId" else "课外材料"
        val picked = text.trim()
        val para = context.trim()

        val filled = tpl
            .replace("{kind}", what)
            .replace("{text}", picked)
            .replace("{source}", source)
            .replace("{context}", if (para.isEmpty()) "" else "原段落：$para")
            .replace("{steps}", stepsFor(kind))

        // 模板里没留 {text} 的话，把选中的内容补在最后，别让它白白丢掉
        val whole = if (tpl.contains("{text}") || picked.isEmpty()) {
            filled
        } else {
            filled.trimEnd() + "\n\n「" + picked + "」"
        }
        return tidy(whole)
    }

    /**
     * 空占位符会留下连续空行，这里压掉；行尾空格也顺手清了。
     * 注意只 trimEnd，模板里的缩进和列表层级保持原样。
     */
    private fun tidy(s: String): String = s
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * 发送。targetPkg 为空表示交给用户在系统选择器里挑。
     * 不论成败都先把文字放进剪贴板，选一次不至于白选。
     */
    fun send(
        context: Context,
        text: String,
        kind: String,
        unitId: Int,
        contextText: String,
        template: String = DEFAULT_TEMPLATE,
        targetPkg: String = YUANBAO_PKG
    ): Outcome {
        val body = prompt(template, text.trim(), kind, unitId, contextText)
        ChatGptSender.copy(context, body, "十词一组")

        val pm = context.packageManager
        val target = targetPkg.trim()
        val installed = target.isNotEmpty() && runCatching {
            pm.getApplicationInfo(target, 0)
        }.isSuccess

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (installed) {
            // 定向的那份另开一个 Intent：直接改 share 会让后面的选择器也被钉死在这个包上，
            // 一旦它其实收不了 text/plain，选择器就成空的了
            val targeted = Intent(share).apply { setPackage(target) }
            val resolved = runCatching {
                pm.resolveActivity(targeted, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull()
            if (resolved != null && runCatching { context.startActivity(targeted) }.isSuccess) {
                return Outcome.SENT
            }
            // 定向分享被拒（没声明接收 text/plain，或部分 ROM 限制），
            // 退回启动 App，文字已在剪贴板里，粘一下就行
            val launch = runCatching { pm.getLaunchIntentForPackage(target) }.getOrNull()
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
