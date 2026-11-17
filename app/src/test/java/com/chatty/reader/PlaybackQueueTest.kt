package com.chatty.reader

import com.chatty.reader.study.PlayNode
import com.chatty.reader.study.Question
import com.chatty.reader.study.Reading
import com.chatty.reader.study.Unit
import com.chatty.reader.study.Word
import com.chatty.reader.study.listIndexOf
import com.chatty.reader.study.playNodes
import com.chatty.reader.study.safeIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整单元连播的顺序与断点换算。
 *
 * 这个顺序同时决定：底部条的「第几段/共几段」、清单里的高亮、以及存进
 * SharedPreferences 的断点数字。三处共用一份数据，所以顺序错一次，三处一起错。
 */
class PlaybackQueueTest {

    private fun word(i: Int) = Word(
        idx = i,
        word = "word$i",
        phonetic = "",
        zh = "",
        guess = "g$i",
        guessQ = "q$i",
        meaningEn = "m$i",
        context = "c$i"
    )

    private fun unit(words: Int = 3, questions: Int = 2) = Unit(
        id = 7,
        words = (1..words).map { word(it) },
        reading = Reading(
            title = "The Cost of Convenience",
            paragraphs = listOf("p1", "p2"),
            questions = (0 until questions).map {
                Question(prompt = "q$it", options = listOf("a", "b"), answer = 0, why = "")
            }
        )
    )

    @Test
    fun coversEverySegmentInOrder() {
        val nodes = unit(words = 3, questions = 2).playNodes()

        // 3 个词 × 3 段 + 阅读 1 段 + 2 道题
        assertEquals(12, nodes.size)

        assertEquals("audio/u007/u007-w01-s1.mp3", nodes[0].key)
        assertEquals("audio/u007/u007-w01-s2.mp3", nodes[1].key)
        assertEquals("audio/u007/u007-w01-s3.mp3", nodes[2].key)
        assertEquals("audio/u007/u007-w02-s1.mp3", nodes[3].key)
        assertEquals("audio/u007/u007-w03-s3.mp3", nodes[8].key)
        assertEquals("audio/u007/u007-reading.mp3", nodes[9].key)
        assertEquals("audio/u007/u007-q1.mp3", nodes[10].key)
        assertEquals("audio/u007/u007-q2.mp3", nodes[11].key)

        assertEquals(PlayNode.Group.READING, nodes[9].group)
        assertEquals(PlayNode.Group.QUESTION, nodes[10].group)
        assertTrue(nodes.filter { it.group == PlayNode.Group.WORD }.all { it.wordIdx != null })
    }

    @Test
    fun shortLabelKeepsWordAndStep() {
        val nodes = unit().playNodes()
        assertEquals("01 word1 · ① 猜一猜", nodes[0].shortLabel())
        assertEquals("02 word2 · ② 释义", nodes[4].shortLabel())
        assertEquals("阅读 · The Cost of Convenience", nodes[9].shortLabel())
        assertEquals("第 1 题", nodes[10].shortLabel())
    }

    @Test
    fun breakpointFallsBackToStart() {
        val nodes = unit().playNodes()
        assertEquals(0, nodes.safeIndex(-1))
        assertEquals(0, nodes.safeIndex(nodes.size))
        assertEquals(0, nodes.safeIndex(999))
        assertEquals(4, nodes.safeIndex(4))
    }

    @Test
    fun singleWordUnitStillHasReadingAndQuestions() {
        val nodes = unit(words = 1, questions = 1).playNodes()
        assertEquals(5, nodes.size)
        assertEquals("audio/u007/u007-w01-s3.mp3", nodes[2].key)
        assertEquals("audio/u007/u007-reading.mp3", nodes[3].key)
        assertEquals("audio/u007/u007-q1.mp3", nodes[4].key)
    }

    @Test
    fun mapsSegmentsToCards() {
        // 3 个词 + 阅读 + 2 题 = 12 段；列表第 0 项是断点卡，词从 1 起
        val nodes = unit(words = 3, questions = 2).playNodes()

        assertEquals(1, nodes.listIndexOf(0, 3))
        assertEquals(1, nodes.listIndexOf(2, 3))  // 词 1 的第三段还是词 1 那张卡
        assertEquals(2, nodes.listIndexOf(3, 3))  // 词 2
        assertEquals(3, nodes.listIndexOf(8, 3))  // 词 3
        assertEquals(4, nodes.listIndexOf(9, 3))  // 阅读
        assertEquals(5, nodes.listIndexOf(10, 3)) // 第 1 题
        assertEquals(6, nodes.listIndexOf(11, 3)) // 第 2 题
        assertNull(nodes.listIndexOf(12, 3))
        assertNull(nodes.listIndexOf(-1, 3))
    }
}
