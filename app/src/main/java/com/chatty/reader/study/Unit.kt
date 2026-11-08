package com.chatty.reader.study

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个学习单元：10 个词（少数单元 3 个）+ 一篇阅读 + 5 道题。
 *
 * 内容全部来自 assets/units/uNN.json，由 Python 流水线生成；
 * 音频在 assets/audio/uNN/ 下，key 就是相对 assets 的路径。
 */
data class Word(
    val idx: Int,
    val word: String,
    val phonetic: String,
    val zh: String,
    val guess: String,
    val guessQ: String,
    val meaningEn: String,
    val context: String
)

data class Question(
    val prompt: String,
    val options: List<String>,
    val answer: Int,
    val why: String
)

data class Reading(
    val title: String,
    val paragraphs: List<String>,
    val questions: List<Question>
)

data class Unit(
    val id: Int,
    val words: List<Word>,
    val reading: Reading
) {
    val title: String get() = "Unit $id"
    fun audio(key: String): String = "audio/u%03d/%s.mp3".format(id, key)
    fun segKey(w: Word, step: Int): String = audio("u%03d-w%02d-s%d".format(id, w.idx, step))
    fun readingKey(): String = audio("u%03d-reading".format(id))
    fun questionKey(i: Int): String = audio("u%03d-q%d".format(id, i + 1))
}

object UnitStore {

    /** 内置 + 已下载，按编号排序 */
    fun listIds(context: Context): List<Int> = runCatching {
        val builtIn = context.assets.list("units")
            ?.mapNotNull { name ->
                Regex("^u(\\d{3})\\.json$").find(name)?.groupValues?.get(1)?.toIntOrNull()
            }
            .orEmpty()
        (builtIn + ContentRepo.installedIds(context)).distinct().sorted()
    }.getOrDefault(emptyList())

    /** 这个单元是包里的还是下载的 */
    fun isDownloaded(context: Context, id: Int): Boolean =
        ContentRepo.unitFile(context, id).exists()

    fun load(context: Context, id: Int): Unit? = runCatching {
        val downloaded = ContentRepo.unitFile(context, id)
        val text = if (downloaded.exists()) {
            downloaded.readText()
        } else {
            context.assets.open("units/u%03d.json".format(id))
                .bufferedReader().use { it.readText() }
        }
        parse(id, JSONObject(text))
    }.getOrNull()

    private fun parse(id: Int, root: JSONObject): Unit {
        val wArr = root.getJSONArray("words")
        val words = buildList {
            for (i in 0 until wArr.length()) {
                val o = wArr.getJSONObject(i)
                add(
                    Word(
                        idx = o.optInt("idx", i + 1),
                        word = o.getString("word"),
                        phonetic = o.optString("phonetic"),
                        zh = o.optString("zh"),
                        guess = o.getString("guess"),
                        guessQ = o.optString("guessQ"),
                        meaningEn = o.getString("meaningEn"),
                        context = o.getString("context")
                    )
                )
            }
        }

        val r = root.getJSONObject("reading")
        val qArr: JSONArray = r.getJSONArray("questions")
        val questions = buildList {
            for (i in 0 until qArr.length()) {
                val o = qArr.getJSONObject(i)
                val opts = o.getJSONArray("options")
                add(
                    Question(
                        prompt = o.getString("q"),
                        options = List(opts.length()) { opts.getString(it) },
                        answer = o.getInt("answer"),
                        why = o.optString("why")
                    )
                )
            }
        }

        return Unit(
            id = id,
            words = words,
            reading = Reading(
                title = r.optString("title", "Reading"),
                paragraphs = r.getString("text").split("\n\n").filter { it.isNotBlank() },
                questions = questions
            )
        )
    }
}
