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
    }
}
