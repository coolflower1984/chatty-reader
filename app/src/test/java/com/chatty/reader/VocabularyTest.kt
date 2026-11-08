package com.chatty.reader

import com.chatty.reader.vocab.PromptBuilder
import com.chatty.reader.vocab.Vocabulary
import com.chatty.reader.vocab.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VocabularyTest {

    private val sample = """
        [
          {"list":1,"group":1,"words":[
            {"no":1,"word":"spite","phonetic":"spait","meaning":"n. 恶意"},
            {"no":2,"word":"luxury","phonetic":"ˈlʌkʃəri","meaning":"n. 奢侈"}
          ]},
          {"list":1,"group":2,"words":[
            {"no":11,"word":"harmony","phonetic":"ˈhɑːməni","meaning":"n. 和谐"}
          ]},
          {"list":2,"group":1,"words":[
            {"no":1,"word":"disguise","phonetic":"","meaning":""}
          ]}
        ]
    """.trimIndent()

    @Test
    fun parse_reads_all_fields() {
        val groups = Vocabulary.parse(sample)
        assertEquals(3, groups.size)
        assertEquals(1, groups[0].listNo)
        assertEquals(1, groups[0].groupNo)
        assertEquals(2, groups[0].words.size)
        assertEquals("spite", groups[0].words[0].word)
        assertEquals("spait", groups[0].words[0].phonetic)
        assertEquals("n. 恶意", groups[0].words[0].meaning)
    }

    @Test
    fun title_and_range_are_readable() {
        val g = Vocabulary.parse(sample)[0]
        assertEquals("List 1 · 第 1 组", g.title)
        assertEquals("1–2", g.numberRange)
        assertEquals("2 个词 · 第 1–2 词", g.subtitle)
    }

    @Test
    fun find_falls_back_to_first_group_of_same_list() {
        val all = Vocabulary.parse(sample)
        val got = Vocabulary.find(all, listNo = 1, groupNo = 99)
        assertEquals(1, got?.listNo)
        assertEquals(1, got?.groupNo)
    }

    @Test
    fun find_falls_back_to_first_group_when_list_missing() {
        val all = Vocabulary.parse(sample)
        val got = Vocabulary.find(all, listNo = 99, groupNo = 1)
        assertEquals(1, got?.listNo)
        assertEquals(1, got?.groupNo)
    }

    @Test
    fun next_walks_forward_and_stops_at_end() {
        val all = Vocabulary.parse(sample)
        assertEquals(1 to 2, Vocabulary.next(all, all[0]))
        assertEquals(2 to 1, Vocabulary.next(all, all[1]))
        // 最后一组：停在原地
        assertEquals(2 to 1, Vocabulary.next(all, all[2]))
    }

    @Test
    fun groups_of_list_are_filtered_and_sorted() {
        val all = Vocabulary.parse(sample)
        val list1 = Vocabulary.groupsOf(all, 1)
        assertEquals(2, list1.size)
        assertEquals(listOf(1, 2), list1.map { it.groupNo })
        assertEquals(listOf(1, 2), Vocabulary.allLists(all))
    }

    /** 真实词库：30 卷、240 组、2224 词，无空词条 */
    @Test
    fun bundled_vocab_is_complete() {
        val file = File("src/main/assets/vocab.json")
        assertTrue("找不到内置词库: ${file.absolutePath}", file.exists())

        val groups = Vocabulary.parse(file.readText())

        assertEquals("卷数", 30, groups.map { it.listNo }.distinct().size)
        assertEquals("组数", 240, groups.size)
        assertEquals("词数", 2224, groups.sumOf { it.words.size })

        assertTrue("存在空组", groups.all { it.words.isNotEmpty() })
        assertTrue("单组超过 10 词", groups.all { it.words.size <= 10 })
        assertTrue("存在空单词", groups.all { g -> g.words.all { it.word.isNotBlank() } })
        assertTrue("单词含中文", groups.all { g -> g.words.none { it.word.any { c -> c.code > 127 } } })

        // 每卷的组号都从 1 开始连续
        groups.groupBy { it.listNo }.forEach { (listNo, gs) ->
            assertEquals("List $listNo 组号不连续", (1..gs.size).toList(), gs.map { it.groupNo }.sorted())
        }
    }

    @Test
    fun word_block_plain_lists_only_spelling() {
        val words = listOf(
            Word(1, "spite", "spait", "n. 恶意"),
            Word(2, "luxury", "ˈlʌkʃəri", "n. 奢侈")
        )
        val block = PromptBuilder.wordBlock(words, withMeaning = false)
        assertEquals("1. spite\n2. luxury", block)
    }

    @Test
    fun word_block_with_meaning_appends_phonetic_and_gloss() {
        val words = listOf(Word(1, "spite", "spait", "n. 恶意"))
        val block = PromptBuilder.wordBlock(words, withMeaning = true)
        assertEquals("1. spite [spait]  n. 恶意", block)
    }

    @Test
    fun build_replaces_placeholder_in_place() {
        val words = listOf(Word(1, "spite", "", ""), Word(2, "luxury", "", ""))
        val text = PromptBuilder.build(
            requirement = "Learn these:\n{words}\nThen quiz me.",
            words = words,
            withMeaning = false
        )
        assertEquals("Learn these:\n1. spite\n2. luxury\nThen quiz me.", text)
    }

    @Test
    fun build_appends_words_when_placeholder_missing() {
        val words = listOf(Word(1, "spite", "", ""))
        val text = PromptBuilder.build("Teach me.", words, withMeaning = false)
        assertEquals("1. spite\n\nTeach me.", text)
    }

    @Test
    fun build_returns_only_block_when_requirement_blank() {
        val words = listOf(Word(7, "zinc", "", ""))
        assertEquals("7. zinc", PromptBuilder.build("   ", words, withMeaning = false))
    }

    @Test
    fun build_keeps_all_ten_words_in_order() {
        val words = (1..10).map { Word(it, "w$it", "", "") }
        val text = PromptBuilder.build("{words}", words, withMeaning = false)
        assertEquals((1..10).joinToString("\n") { "$it. w$it" }, text)
    }

    /** 默认模板必须保住“猜词 → 词义 → 六级语境 → 阅读理解”这条主线 */
    @Test
    fun default_requirement_covers_the_whole_flow() {
        val t = AppConfig.DEFAULT_REQUIREMENT

        assertTrue("缺少 {words} 占位符", t.contains("{words}"))
        assertTrue("缺少 Guess 段", t.contains("Guess:"))
        assertTrue("缺少 Meaning 段", t.contains("Meaning:"))
        assertTrue("缺少 Context 段", t.contains("Context:"))
        assertTrue("缺少 Reading Comprehension", t.contains("Reading Comprehension"))
        assertTrue("缺少选择题要求", t.contains("multiple-choice"))
        assertTrue("缺少答案键要求", t.contains("answer key"))
    }
}
