package com.chatty.reader.study

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 内容仓库：内置单元在 assets，追更的单元在 filesDir/content。
 *
 * 更新包是一个 zip：manifest.json + units/uNNN.json + audio/uNNN 下的 mp3。
 * 解压目录和 assets 同名同构，所以读取逻辑只有一处差别 —— 先看文件，再看 assets。
 */
object ContentRepo {

    private const val DIR = "content"

    fun root(context: Context): File = File(context.filesDir, DIR)

    fun unitFile(context: Context, id: Int): File =
        File(root(context), "units/u%03d.json".format(id))

    fun audioFile(context: Context, key: String): File = File(root(context), key)

    /** 已下载的单元编号 */
    fun installedIds(context: Context): List<Int> = runCatching {
        File(root(context), "units")
            .listFiles()
            ?.mapNotNull { Regex("^u(\\d{3})\\.json$").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            ?.sorted()
            .orEmpty()
    }.getOrDefault(emptyList())

    fun installedBytes(context: Context): Long = runCatching {
        root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    data class Result(val ok: Boolean, val added: List<Int>, val message: String)

    /**
     * 解压一个更新包。先落到临时目录，全部成功才合并，
     * 中途异常不会留下半套内容（半套内容会让某个单元读到缺页）。
     */
    fun importZip(context: Context, uri: Uri): Result {
        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: return Result(false, emptyList(), "打不开这个文件")
        return input.use { importStream(context, it) }
    }

    /** 同一个包已经落在本地文件里（在线下载），免去再走一次文件选择器 */
    fun importFile(context: Context, file: File): Result {
        if (!file.exists()) return Result(false, emptyList(), "文件不存在")
        val input = runCatching { file.inputStream().buffered() }.getOrNull()
            ?: return Result(false, emptyList(), "文件打不开")
        return input.use { importStream(context, it) }
    }

    private fun importStream(context: Context, input: InputStream): Result {
        val tmp = File(context.cacheDir, "import_tmp")
        tmp.deleteRecursively()
        if (!tmp.mkdirs()) return Result(false, emptyList(), "无法创建临时目录")

        val added = mutableListOf<Int>()
        return try {
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (!entry.isDirectory) {
                        if (name.contains("..")) throw IllegalStateException("包内路径非法")
                        val out = File(tmp, name)
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it, 64 * 1024) }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                if (count == 0) throw IllegalStateException("包是空的")
            }

            val unitsDir = File(tmp, "units")
            val ids = unitsDir.listFiles()
                ?.mapNotNull { Regex("^u(\\d{3})\\.json$").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
                ?.sorted()
                .orEmpty()
            if (ids.isEmpty()) return Result(false, emptyList(), "包里没有单元文件")

            // 校验每个单元的 JSON 能解析、音频文件齐，缺一个就整包拒绝
            for (id in ids) {
                val text = File(unitsDir, "u%03d.json".format(id)).readText()
                val obj = JSONObject(text)
                val words = obj.getJSONArray("words")
                val need = mutableListOf("audio/u%03d/u%03d-reading.mp3".format(id, id))
                for (i in 1..words.length()) need += "audio/u%03d/u%03d-w%02d-s1.mp3".format(id, id, i)
                for (n in 1..5) need += "audio/u%03d/u%03d-q%d.mp3".format(id, id, n)
                val missing = need.filterNot { File(tmp, it).exists() }
                if (missing.isNotEmpty()) {
                    throw IllegalStateException("u%03d 缺少 %d 个音频".format(id, missing.size))
                }
            }

            // 合并：旧文件先删同名单元，避免残留旧音频
            val dest = root(context)
            val destUnits = File(dest, "units").apply { mkdirs() }
            for (id in ids) {
                File(destUnits, "u%03d.json".format(id)).delete()
                File(dest, "audio/u%03d".format(id)).deleteRecursively()
            }
            val packUnits = File(tmp, "units")
            packUnits.listFiles()?.forEach { it.copyTo(File(destUnits, it.name), overwrite = true) }
            val packAudio = File(tmp, "audio")
            if (packAudio.exists()) {
                packAudio.listFiles()?.forEach { dir ->
                    val target = File(dest, "audio/${dir.name}")
                    target.mkdirs()
                    dir.listFiles()?.forEach { it.copyTo(File(target, it.name), overwrite = true) }
                }
            }
            added += ids
            Result(true, added, "已导入 %d 个单元".format(ids.size))
        } catch (e: Exception) {
            Result(false, emptyList(), e.message ?: "导入失败")
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** 删掉某个下载的单元（内置的删不掉） */
    fun remove(context: Context, id: Int): Boolean = runCatching {
        unitFile(context, id).delete() and File(root(context), "audio/u%03d".format(id)).deleteRecursively()
    }.getOrDefault(false)
}
