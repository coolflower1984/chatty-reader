package com.chatty.reader.study

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 学习进度。
 *
 * 判定故意做得很松：一个词有三段音频（猜一猜 / 释义 / 六级语境），
 * **任意一段**开始播，这个词就算学过 —— 不用额外点「完成」。
 * 阅读和题目不算，它们不属于任何一个词。
 *
 * 存储用 SharedPreferences：240 个单元满打满算几十 KB，一次全读进内存也不心疼。
 */
object StudyProgress {

    private const val PREFS = "study_progress"
    private const val K_LAST_UNIT = "last_unit"
    private const val K_LAST_WORD = "last_word"
    private const val K_LAST_AT = "last_at"

    private const val PREFIX = "p"

    /** 进度一变就 +1。界面读它，就能跟着重算 */
    var version by mutableIntStateOf(0)
        private set

    data class Last(val unitId: Int, val wordIdx: Int, val at: Long)

    private fun sp(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun keyOf(unitId: Int) = "$PREFIX$unitId"

    /** 这个单元里听过的词的编号 */
    fun played(context: Context, unitId: Int): Set<Int> =
        parse(sp(context).getString(keyOf(unitId), null))

    private fun parse(raw: String?): Set<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()

    /** 所有单元的进度，列表一次性读完 */
    fun snapshot(context: Context): Map<Int, Set<Int>> = buildMap {
        sp(context).all.forEach { (k, v) ->
            if (!k.startsWith(PREFIX)) return@forEach
            val id = k.substring(PREFIX.length).toIntOrNull() ?: return@forEach
            val set = parse(v as? String)
            if (set.isNotEmpty()) put(id, set)
        }
    }

    fun learnedCount(context: Context): Int = snapshot(context).values.sumOf { it.size }

    fun last(context: Context): Last? {
        val s = sp(context)
        val unit = s.getInt(K_LAST_UNIT, 0)
        if (unit <= 0) return null
        return Last(
            unitId = unit,
            wordIdx = s.getInt(K_LAST_WORD, 1),
            at = s.getLong(K_LAST_AT, 0L)
        )
    }

    /** 某个词听过任意一段音频 */
    fun markWord(context: Context, unitId: Int, wordIdx: Int) {
        val have = played(context, unitId)
        if (wordIdx !in have) {
            sp(context).edit()
                .putString(keyOf(unitId), (have + wordIdx).sorted().joinToString(","))
                .apply()
            version++
        }
        touch(context, unitId, wordIdx)
    }

    /** 进单元时记一笔：单元位置更新，续读点落在第一个没学过的词上 */
    fun markOpen(context: Context, unitId: Int, wordIdxs: List<Int>) {
        val done = played(context, unitId)
        // 都学完了就停在原来那个词上，别把进度条位置倒回第一个
        val next = wordIdxs.firstOrNull { it !in done }
            ?: last(context)?.takeIf { it.unitId == unitId }?.wordIdx
            ?: wordIdxs.firstOrNull()
            ?: 1
        touch(context, unitId, next)
    }

    private fun touch(context: Context, unitId: Int, wordIdx: Int) {
        val s = sp(context)
        if (s.getInt(K_LAST_UNIT, 0) == unitId && s.getInt(K_LAST_WORD, 0) == wordIdx) return
        val now = System.currentTimeMillis()
        s.edit()
            .putInt(K_LAST_UNIT, unitId)
            .putInt(K_LAST_WORD, wordIdx)
            .putLong(K_LAST_AT, now)
            // 每单元再留一份自己的时间戳和位置，重置某个单元时才能回退到次新的那个
            .putLong("t$unitId", now)
            .putInt("w$unitId", wordIdx)
            .apply()
        version++
    }

    fun resetUnit(context: Context, unitId: Int) {
        val s = sp(context)
        s.edit()
            .remove(keyOf(unitId))
            .remove("t$unitId")
            .remove("w$unitId")
            .apply()
        if (s.getInt(K_LAST_UNIT, 0) == unitId) recomputeLast(context)
        version++
    }

    /** 重置掉「当前单元」之后，把「上次学到哪」挪回剩下最近的单元 */
    private fun recomputeLast(context: Context) {
        val s = sp(context)
        var bestId = 0
        var bestAt = -1L
        s.all.forEach { (k, v) ->
            if (!k.startsWith("t")) return@forEach
            val id = k.substring(1).toIntOrNull() ?: return@forEach
            val at = (v as? Long) ?: 0L
            if (at > bestAt) {
                bestAt = at
                bestId = id
            }
        }
        if (bestId > 0) {
            s.edit()
                .putInt(K_LAST_UNIT, bestId)
                .putInt(K_LAST_WORD, s.getInt("w$bestId", 1))
                .putLong(K_LAST_AT, bestAt)
                .apply()
        } else {
            s.edit().remove(K_LAST_UNIT).remove(K_LAST_WORD).remove(K_LAST_AT).apply()
        }
    }

    fun resetAll(context: Context) {
        sp(context).edit().clear().apply()
        version++
    }

    // ---------- 从音频路径反推 ----------

    /** audio/u001/u001-w03-s1.mp3 → (1, 3)。阅读和题目匹配不上，直接忽略 */
    private val WORD_KEY = Regex("""audio/u(\d{3})/u\d{3}-w(\d{2})-s\d\.mp3""")

    fun wordOf(key: String): Pair<Int, Int>? {
        val m = WORD_KEY.find(key) ?: return null
        val unit = m.groupValues[1].toIntOrNull() ?: return null
        val word = m.groupValues[2].toIntOrNull() ?: return null
        return unit to word
    }

    /** SegmentPlayer 每开一段就调它 */
    fun onSegment(context: Context, key: String) {
        val (unit, word) = wordOf(key) ?: return
        markWord(context, unit, word)
    }
}
