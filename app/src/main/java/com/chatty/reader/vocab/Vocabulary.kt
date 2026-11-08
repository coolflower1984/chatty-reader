package com.chatty.reader.vocab

import android.content.Context
import org.json.JSONArray

/** 一个词条：序号、拼写、音标、中文释义 */
data class Word(
    val no: Int,
    val word: String,
    val phonetic: String,
    val meaning: String
)

/** 一组十个词（部分卷末组不足十个） */
data class WordGroup(
    val listNo: Int,
    val groupNo: Int,
    val words: List<Word>
) {
    /** 例如 “List 3 · 第 2 组” */
    val title: String get() = "List $listNo · 第 $groupNo 组"

    /** 该组在全书中的词序范围，例如 “21–30” */
    val numberRange: String
        get() = when {
            words.isEmpty() -> "—"
            words.size == 1 -> "${words.first().no}"
            else -> "${words.first().no}–${words.last().no}"
        }

    val subtitle: String get() = "${words.size} 个词 · 第 $numberRange 词"
}

object Vocabulary {

    private const val ASSET_NAME = "vocab.json"

    /** 从 assets 读取整本词库，一次性解析；调用方应在 IO 线程执行 */
    fun load(context: Context): List<WordGroup> =
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            .let(::parse)

    fun parse(json: String): List<WordGroup> {
        val root = JSONArray(json)
        val out = ArrayList<WordGroup>(root.length())
        for (i in 0 until root.length()) {
            val group = root.getJSONObject(i)
            val wordsJson = group.getJSONArray("words")
            val words = ArrayList<Word>(wordsJson.length())
            for (j in 0 until wordsJson.length()) {
                val w = wordsJson.getJSONObject(j)
                words += Word(
                    no = w.optInt("no", j + 1),
                    word = w.optString("word").trim(),
                    phonetic = w.optString("phonetic").trim(),
                    meaning = w.optString("meaning").trim()
                )
            }
            out += WordGroup(
                listNo = group.getInt("list"),
                groupNo = group.getInt("group"),
                words = words
            )
        }
        return out
    }

    /** 某一卷的全部组，按组号升序 */
    fun groupsOf(all: List<WordGroup>, listNo: Int): List<WordGroup> =
        all.filter { it.listNo == listNo }.sortedBy { it.groupNo }

    fun allLists(all: List<WordGroup>): List<Int> =
        all.map { it.listNo }.distinct().sorted()

    /** 按卷号与组号取组；找不到时退回第一组，避免界面空转 */
    fun find(all: List<WordGroup>, listNo: Int, groupNo: Int): WordGroup? {
        all.firstOrNull { it.listNo == listNo && it.groupNo == groupNo }?.let { return it }
        return all.firstOrNull { it.listNo == listNo } ?: all.firstOrNull()
    }

    /** 下一组的“坐标”，走到最后一组时停在原地 */
    fun next(all: List<WordGroup>, current: WordGroup): Pair<Int, Int> {
        val index = all.indexOfFirst { it.listNo == current.listNo && it.groupNo == current.groupNo }
        if (index < 0 || index + 1 >= all.size) return current.listNo to current.groupNo
        val n = all[index + 1]
        return n.listNo to n.groupNo
    }
}
