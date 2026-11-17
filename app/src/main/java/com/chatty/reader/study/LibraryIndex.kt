package com.chatty.reader.study

/**
 * 列表页的检索与分段。
 *
 * 单元一多，一路滑下去找 Unit 137 是件很蠢的事。这里把 240 个单元按每 10 个
 * 切一段（正好对上一个更新包），段可以折叠；搜索框则认单元号、单词、中文释义
 * 和阅读标题，三种写法都落在同一份数据上。
 *
 * 全是纯函数，界面只负责把返回的行画出来，所以可以直接单测。
 */
data class UnitSection(
    /** 段号，用段首单元号当 key，稳定且可读 */
    val key: String,
    val from: Int,
    val to: Int,
    val unitIds: List<Int>,
    /** 段内已学词数 */
    val learned: Int,
    /** 段内总词数 */
    val total: Int
)

sealed interface ListRow {
    data class Header(val section: UnitSection, val collapsed: Boolean) : ListRow

    /** [note] 是搜索命中的说明，正常浏览时为 null */
    data class UnitLine(val unitId: Int, val note: String?) : ListRow
}

object LibraryIndex {

    const val PER_PACK = 10

    /** 按 (id - 1) / 10 分段，中间缺单元也不会把后面的段带歪 */
    fun sections(units: List<Unit>, learned: Map<Int, Set<Int>>): List<UnitSection> =
        units.groupBy { (it.id - 1) / PER_PACK }
            .toSortedMap()
            .map { (bucket, group) ->
                val ids = group.map { it.id }.sorted()
                UnitSection(
                    key = (bucket * PER_PACK + 1).toString(),
                    from = bucket * PER_PACK + 1,
                    to = ids.last(),
                    unitIds = ids,
                    learned = ids.sumOf { learned[it]?.size ?: 0 },
                    total = group.sumOf { it.words.size }
                )
            }

    /**
     * 搜索。
     *
     * 数字当单元号：`37`、`unit 37`、`u37` 都只回 Unit 37（有精确命中就不再
     * 返回 137、237 这些含 "37" 的），找不到精确命中时才退化成包含匹配。
     * 其余按顺序找：单词 > 中文释义 > 阅读标题。
     */
    fun search(units: List<Unit>, query: String): List<ListRow.UnitLine> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val idQuery = Regex("""^(?:unit|u)?\s*(\d{1,3})$""")
            .find(q)?.groupValues?.get(1)?.toIntOrNull()
        if (idQuery != null) {
            val exact = units.filter { it.id == idQuery }.sortedBy { it.id }
            if (exact.isNotEmpty()) {
                return exact.map { ListRow.UnitLine(it.id, "单元号") }
            }
        }

        return units.sortedBy { it.id }.mapNotNull { u ->
            val word = u.words.firstOrNull {
                it.word.lowercase().contains(q) || it.zh.lowercase().contains(q)
            }
            when {
                word != null -> ListRow.UnitLine(u.id, hitNote(word, q))
                idQuery != null && u.id.toString().contains(idQuery.toString()) ->
                    ListRow.UnitLine(u.id, "单元号")
                u.reading.title.lowercase().contains(q) ->
                    ListRow.UnitLine(u.id, "阅读 · ${u.reading.title}")
                else -> null
            }
        }
    }

    private fun hitNote(word: Word, q: String): String {
        val head = "第 ${word.idx} 个词 · ${word.word}"
        return if (word.word.lowercase().contains(q)) head
        else "$head · ${word.zh.lineSequence().first().take(16)}"
    }

    /**
     * 把段和折叠状态摊平成列表行。
     *
     * [hits] 不为 null 表示正在搜索：不再插段头，直接一列结果，
     * 免得「找到 3 个」中间还夹着两条无关的分组标题。
     */
    fun rows(
        sections: List<UnitSection>,
        collapsed: Set<String>,
        hits: List<ListRow.UnitLine>?
    ): List<ListRow> {
        if (hits != null) return hits
        return buildList {
            sections.forEach { s ->
                val shut = s.key in collapsed
                add(ListRow.Header(s, shut))
                if (!shut) s.unitIds.forEach { add(ListRow.UnitLine(it, null)) }
            }
        }
    }

    fun rowIndexOfSection(rows: List<ListRow>, key: String): Int =
        rows.indexOfFirst { it is ListRow.Header && it.section.key == key }

    fun rowIndexOfUnit(rows: List<ListRow>, unitId: Int): Int =
        rows.indexOfFirst { it is ListRow.UnitLine && it.unitId == unitId }
}
