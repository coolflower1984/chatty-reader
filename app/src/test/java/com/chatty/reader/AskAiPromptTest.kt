package com.chatty.reader

import com.chatty.reader.share.AskAi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 发给 AI 的那段话，重点是别把用户选的东西弄丢 */
class AskAiPromptTest {

    @Test
    fun keepsSelectedWordAndSource() {
        val p = AskAi.prompt("convenience", "word", 12, "The cost of convenience is rarely counted.")
        assertTrue(p.contains("convenience"))
        assertTrue(p.contains("Unit 12"))
        assertTrue(p.contains("音标"))
        assertFalse(p.contains("拆句子结构"))
    }

    @Test
    fun sentenceGetsGrammarInstructions() {
        val p = AskAi.prompt(
            "What matters is not how fast you read but how much you keep.",
            "sentence",
            7,
            ""
        )
        assertTrue(p.contains("拆句子结构"))
        assertTrue(p.contains("What matters is not"))
        assertFalse(p.contains("音标"))
    }

    @Test
    fun emptyContextDoesNotAddBlankLine() {
        val p = AskAi.prompt("brief", "word", 0, "")
        assertFalse(p.contains("原段落"))
        assertTrue(p.contains("课外材料"))
        assertFalse(p.contains("\n\n\n"))
    }

    // ---------- 提示词可以在设置里改 ----------

    @Test
    fun customTemplateFillsEveryPlaceholder() {
        val tpl = "请讲解 {kind} {text}，来自 {source}。\n{context}\n步骤：\n{steps}"
        val p = AskAi.prompt(tpl, "brief", "word", 3, "A brief pause.")
        assertTrue(p.startsWith("请讲解 单词 brief，来自 Unit 3。"))
        assertTrue(p.contains("原段落：A brief pause."))
        assertTrue(p.contains("音标"))
        assertFalse(p.contains("{"))
    }

    @Test
    fun templateWithoutTextStillCarriesSelection() {
        val p = AskAi.prompt("帮我讲讲这个词，用中文。", "resilient", "word", 0, "")
        assertTrue(p.startsWith("帮我讲讲这个词，用中文。"))
        assertTrue(p.contains("resilient"))
    }

    @Test
    fun blankTemplateFallsBackToDefault() {
        val p = AskAi.prompt("   ", "grasp", "word", 9, "")
        assertTrue(p.contains("grasp"))
        assertTrue(p.contains("Unit 9"))
        assertTrue(p.contains("记忆点"))
    }

    @Test
    fun customTemplateIsNotRewritten() {
        // 用户自己写的缩进和列表层级要原样保留
        val tpl = "1. 先翻译\n    - 保留这层缩进\n2. 再讲语法"
        val p = AskAi.prompt(tpl, "一句话", "sentence", 0, "")
        assertTrue(p.contains("    - 保留这层缩进"))
    }
}
