package com.chatty.reader

import com.chatty.reader.study.StudyProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 从音频路径反推「哪个单元的第几个词」。
 *
 * 只有单词的三段音频算数；阅读和题目属于整篇，不该记到某个词头上 ——
 * 这里错一个，进度条就会莫名其妙地涨。
 */
class StudyProgressTest {

    @Test
    fun readsWordSegment() {
        assertEquals(1 to 3, StudyProgress.wordOf("audio/u001/u001-w03-s1.mp3"))
        assertEquals(240 to 10, StudyProgress.wordOf("audio/u240/u240-w10-s3.mp3"))
    }

    @Test
    fun ignoresReadingAndQuestions() {
        assertNull(StudyProgress.wordOf("audio/u012/u012-reading.mp3"))
        assertNull(StudyProgress.wordOf("audio/u012/u012-q1.mp3"))
    }

    @Test
    fun ignoresJunk() {
        assertNull(StudyProgress.wordOf(""))
        assertNull(StudyProgress.wordOf("u012-w01-s1.mp3"))
        assertNull(StudyProgress.wordOf("audio/u12/u12-w1-s1.mp3"))
    }
}
