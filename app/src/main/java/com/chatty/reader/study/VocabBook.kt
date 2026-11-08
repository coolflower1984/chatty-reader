package com.chatty.reader.study

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生词本：阅读时选中的词或句子存这里。
 *
 * 只有一个 JSON 文件，几十上百条的量级，整读整写足够；
 * 全量重写反而比逐条改更不容易写坏。
 */
object VocabBook {

    private const val DIR = "vocab"
    private const val FILE = "book.json"

    data class Entry(
        val id: String,
        val text: String,
        val kind: String,      // "word" / "sentence"
        val unitId: Int,
        val context: String,   // 所在段落，复习时能想起当时那句话
        val createdAt: Long
    ) {
        val isWord: Boolean get() = kind == "word"
        val source: String get() = if (unitId > 0) "Unit $unitId" else "手动添加"
        val dateText: String
            get() = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(createdAt))
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    private fun file(context: Context): File = File(dir(context), FILE)

    @Synchronized
    fun list(context: Context): List<Entry> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Entry(
                        id = o.getString("id"),
                        text = o.getString("text"),
                        kind = o.optString("kind", "word"),
                        unitId = o.optInt("unitId", 0),
                        context = o.optString("context"),
                        createdAt = o.optLong("createdAt", 0L)
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun count(context: Context): Int = list(context).size

    /** 同一条内容重复入库没意义，返回 false 表示已经在了 */
    @Synchronized
    fun add(
        context: Context,
        text: String,
        kind: String,
        unitId: Int,
        contextText: String
    ): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val all = list(context)
        if (all.any { it.text.equals(clean, ignoreCase = true) && it.unitId == unitId }) return false

        val entry = Entry(
            id = System.currentTimeMillis().toString() + "-" + (1000..9999).random(),
            text = clean,
            kind = kind,
            unitId = unitId,
            context = contextText.trim().take(400),
            createdAt = System.currentTimeMillis()
        )
        return write(context, all + entry)
    }

    @Synchronized
    fun remove(context: Context, id: String): Boolean {
        val rest = list(context).filterNot { it.id == id }
        return write(context, rest)
    }

    @Synchronized
    fun clear(context: Context): Boolean = write(context, emptyList())

    private fun write(context: Context, entries: List<Entry>): Boolean = runCatching {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("text", e.text)
                    .put("kind", e.kind)
                    .put("unitId", e.unitId)
                    .put("context", e.context)
                    .put("createdAt", e.createdAt)
            )
        }
        file(context).writeText(arr.toString())
        true
    }.getOrDefault(false)
}
