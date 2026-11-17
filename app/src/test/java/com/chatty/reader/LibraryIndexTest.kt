package com.chatty.reader

import com.chatty.reader.study.LibraryIndex
import com.chatty.reader.study.ListRow
import com.chatty.reader.study.Reading
import com.chatty.reader.study.Unit
import com.chatty.reader.study.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表页的分段、搜索与折叠。
 *
 * 这三件事决定「找一个单元要滑几下」，错一处就是用户多滑十几屏，
 * 所以把纯逻辑单独拎出来测，界面只负责画。
 */
class LibraryIndexTest {

    private fun word(i: Int, en: String, zh: String = "释义$i") = Word(
        idx = i,
        word = en,
        phonetic = "",
        zh = zh,
        guess = "",
        guessQ = "",
        meaningEn = "",
        context = ""
    )

    private fun unit(
        id: Int,
        words: List<Word> = listOf(word(1, "abandon")),
        reading: String = "A Title"
    ) = Unit(
        id = id,
        words = words,
        reading = Reading(title = reading, paragraphs = listOf("p"), questions = emptyList())
    )

    @Test
    fun splitsEveryTenUnits() {
        val sections = LibraryIndex.sections((1..13).map { unit(it) }, emptyMap())

        assertEquals(2, sections.size)
        assertEquals("1", sections[0].key)
        assertEquals(1, sections[0].from)
        assertEquals(10, sections[0].to)
        assertEquals(10, sections[0].unitIds.size)
        assertEquals(11, sections[1].from)
        assertEquals(13, sections[1].to)
        assertEquals(3, sections[1].unitIds.size)
    }

    @Test
    fun sectionsSurviveGaps() {
        // 只装了 11–20 和 31–40，第二段仍要从 31 起，不能按「第几个」重新编号
        val units = ((11..20) + (31..40)).map { unit(it) }
        val sections = LibraryIndex.sections(units, emptyMap())

        assertEquals(2, sections.size)
        assertEquals(11, sections[0].from)
        assertEquals(20, sections[0].to)
        assertEquals(31, sections[1].from)
        assertEquals(40, sections[1].to)
    }

    @Test
    fun countsLearnedWordsPerSection() {
        val units = (1..11).map { unit(it) }
        val learned = mapOf(1 to setOf(1, 2), 2 to setOf(3), 11 to setOf(1))
        val sections = LibraryIndex.sections(units, learned)

        assertEquals(3, sections[0].learned)
        assertEquals(1, sections[1].learned)
    }

    @Test
    fun searchesUnitNumberExactly() {
        val units = (1..120).map { unit(it) }

        // 有精确命中时，不再把 137、237 一起捞出来
        assertEquals(listOf(37), LibraryIndex.search(units, "37").map { it.unitId })
        assertEquals(listOf(37), LibraryIndex.search(units, "unit 37").map { it.unitId })
        assertEquals(listOf(37), LibraryIndex.search(units, "u37").map { it.unitId })
    }

    @Test
    fun searchesWordAndMeaning() {
        val units = listOf(
            unit(5, listOf(word(1, "abandon", "v. 放弃"), word(2, "ability"))),
            unit(6, listOf(word(1, "reform")))
        )

        assertEquals(5, LibraryIndex.search(units, "aband").single().unitId)
        assertEquals(5, LibraryIndex.search(units, "放弃").single().unitId)
        assertTrue(LibraryIndex.search(units, "放弃").single().note!!.contains("第 1 个词"))
    }

    @Test
    fun fallsBackToReadingTitle() {
        val units = listOf(unit(3, reading = "The Small Library"), unit(4, reading = "Other"))

        val hit = LibraryIndex.search(units, "library").single()
        assertEquals(3, hit.unitId)
        assertTrue(hit.note!!.startsWith("阅读"))
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertTrue(LibraryIndex.search(listOf(unit(1)), "   ").isEmpty())
    }

    @Test
    fun collapsedSectionHidesItsUnits() {
        val sections = LibraryIndex.sections((1..20).map { unit(it) }, emptyMap())

        val open = LibraryIndex.rows(sections, emptySet(), null)
        assertEquals(22, open.size) // 20 个单元 + 2 个段头

        val shut = LibraryIndex.rows(sections, setOf("1"), null)
        assertEquals(12, shut.size) // 2 个段头 + 第二段的 10 个单元
        assertEquals(0, LibraryIndex.rowIndexOfSection(shut, "1"))
        assertEquals(-1, LibraryIndex.rowIndexOfUnit(shut, 5))
    }

    @Test
    fun searchResultsSkipHeaders() {
        val sections = LibraryIndex.sections((1..20).map { unit(it) }, emptyMap())
        val rows = LibraryIndex.rows(sections, emptySet(), LibraryIndex.search((1..20).map { unit(it) }, "7"))

        assertEquals(1, rows.size)
        assertTrue(rows.all { it is ListRow.UnitLine })
        // 搜索模式下没有段头，滚动下标就是结果本身的位置
        assertEquals(0, LibraryIndex.rowIndexOfUnit(rows, 7))
    }
}
