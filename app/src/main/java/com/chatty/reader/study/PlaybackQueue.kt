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
 * 一段播完后该走哪一段。
 *
 * @param from 刚播完的下标
 * @param size 队列总段数
 * @param wordCount 队列前部有多少个词（每个词 [span] 段）；0 表示这条队列里没有词
 * @param repeatTimes 每个词播几遍，<=0 视为不限
 * @param round 当前这个词已经播完几遍
 * @param loop 整单元播完后是否从头再来
 * @return 下一段下标 + 更新后的遍数；null 表示整轮结束
 *
 * 重复只发生在词内部：③ 播完回到同一个词的 ①，而不是回到第一个词；
 * 阅读和题目永远只播一遍。
 */
fun nextPlayStep(
    from: Int,
    size: Int,
    wordCount: Int,
    repeatTimes: Int,
    round: Int,
    loop: Boolean,
    span: Int = 3
): Pair<Int, Int>? {
    if (size <= 0) return null
    var r = round
    val wordsEnd = wordCount * span
    if (span > 0 && from in 0 until wordsEnd) {
        // 这一段是不是它所在词的最后一个
        val wordEnd = ((from / span) + 1) * span - 1
        if (from == wordEnd) {
            r += 1
            val times = if (repeatTimes <= 0) Int.MAX_VALUE else repeatTimes
            if (r < times) return (from - span + 1) to r
            r = 0
        }
    }
    val next = from + 1
    return when {
        next < size -> next to r
        loop -> 0 to 0
        else -> null
    }
}

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
