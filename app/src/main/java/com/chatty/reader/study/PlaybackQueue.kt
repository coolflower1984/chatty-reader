package com.chatty.reader.study

/**
 * 整单元连播的播放顺序：词 1 的三段 → 词 2 的三段 → … → 阅读全文 → 5 道题。
 *
 * 单独拎出来是因为「第几段」这个序号要被三处共用：连播底部条的进度、
 * 节点清单里的高亮、以及退出后存进 SharedPreferences 的断点。
 * 顺序一旦改了，这三处必须同时改，所以只留一个真值来源。
 */
data class PlayNode(
    val key: String,
    /** 清单第一行：词形 / 阅读标题 / 第几题 */
    val title: String,
    /** 清单第二行：① 猜一猜 / 全文 / 题干与选项 */
    val step: String,
    val group: Group,
    val wordIdx: Int? = null
) {
    enum class Group { WORD, READING, QUESTION }

    /** 底部条上的短标签，例如「03 abandon · ② 释义」 */
    fun shortLabel(): String = when (group) {
        Group.WORD -> "%02d %s · %s".format(wordIdx ?: 0, title, step)
        Group.READING -> "阅读 · $title"
        Group.QUESTION -> title
    }
}

fun Unit.playNodes(): List<PlayNode> = buildList {
    words.forEach { w ->
        add(PlayNode(segKey(w, 1), w.word, "① 猜一猜", PlayNode.Group.WORD, w.idx))
        add(PlayNode(segKey(w, 2), w.word, "② 释义", PlayNode.Group.WORD, w.idx))
        add(PlayNode(segKey(w, 3), w.word, "③ 六级语境", PlayNode.Group.WORD, w.idx))
    }
    add(
        PlayNode(
            key = readingKey(),
            title = reading.title.ifBlank { "Reading" },
            step = "全文",
            group = PlayNode.Group.READING
        )
    )
    reading.questions.forEachIndexed { i, _ ->
        add(
            PlayNode(
                key = questionKey(i),
                title = "第 ${i + 1} 题",
                step = "题干与选项",
                group = PlayNode.Group.QUESTION
            )
        )
    }
}

/** 断点存在第几段；越界或没有就返回 0，调用方不必自己校验 */
fun List<PlayNode>.safeIndex(index: Int): Int = if (index in indices) index else 0

/**
 * 连播第 [index] 段，对应阅读页列表的第几项。
 *
 * 列表第 0 项固定留给断点卡，词从 1 开始，阅读紧随其后，题目再往后。
 * 自动跟随滚动靠这个换算，错一位就会滚到上一张卡。
 */
fun List<PlayNode>.listIndexOf(index: Int, wordCount: Int): Int? {
    val node = getOrNull(index) ?: return null
    return when (node.group) {
        // 每个词固定三段，所以第 index 段落在第 index / 3 个词上
        PlayNode.Group.WORD -> 1 + index / 3
        PlayNode.Group.READING -> 1 + wordCount
        PlayNode.Group.QUESTION -> 1 + wordCount + 1 + (index - wordCount * 3 - 1)
    }
}
