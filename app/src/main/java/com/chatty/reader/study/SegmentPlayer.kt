package com.chatty.reader.study

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 播放一段或一串 assets 里的 mp3。
 *
 * 只用 MediaPlayer 一个实例，靠 reset 换源 —— 一轮朗读有几十段，
 * 每段新建一个实例会在低端机上攒出一堆 GC 压力。
 *
 * 队列有三种用法：点单段 / 词内三段用 [play]，整单元连播用 [playFrom]，
 * 后者会把 [wholeUnit] 置 true，界面据此决定要不要显示连播条。
 *
 * 「每词播几遍」和「整单元循环」是播放器内部的事：一轮重复不产生新的队列下标，
 * 所以清单、断点、进度条都不用跟着变。
 */
class SegmentPlayer(
    private val context: Context,
    /** 每开一段就回调一次，用来记学习进度；连播时后续片段也会走到这里 */
    // 这里必须写全限定名：同包下有个 study.Unit，裸写 Unit 会被当成那个类
    private val onSegment: ((String) -> kotlin.Unit)? = null,
    /** 每开一段回调当前队列下标，用来存连播断点 */
    // 同样要全限定：这个包里 Unit 是学习单元那个类
    private val onPosition: ((Int) -> kotlin.Unit)? = null,
    /** 整串播完（不是手动停）时回调一次，界面据此把断点清掉 */
    private val onFinished: (() -> kotlin.Unit)? = null
) {

    private val player = MediaPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private var queue: List<String> = emptyList()
    private var pos = 0

    /** 队列前部有几个词；只有连播和词内三段才非 0，点单段时为 0 */
    private var wordCount = 0

    /** 等间隔的那一下：= -1 表示没有待播的下一段 */
    private var pendingNext = -1

    /** 当前这个词已经播完几遍，用于「每词 N 遍」 */
    private var round = 0

    /** 段与段之间空多久；1 秒足够把上一句在脑子里过一遍 */
    var gapMs: Long = 1_000L

    /** 每个词播几遍：1 / 2 / 3，<=0 视为不限（一直重复这个词，直到手动下一段） */
    var wordRepeat: Int = 1

    /** 整单元播完后是否从头再来 */
    var loopUnit: Boolean = false

    private val gapTick = Runnable { advanceNow() }

    /** 当前正在播（或暂停在）哪一段；null 表示空闲 */
    var current by mutableStateOf<String?>(null)
        private set

    var playing by mutableStateOf(false)
        private set

    /** 当前段在队列里的下标，-1 表示没在队列里 */
    var index by mutableIntStateOf(-1)
        private set

    /** 当前队列长度 */
    var size by mutableIntStateOf(0)
        private set

    /** 是否是「整单元连播」；点单段或词内三段时为 false */
    var wholeUnit by mutableStateOf(false)
        private set

    /** 是否正停在两段之间的空档里（此时没有声音，但仍在「播放」状态） */
    var inGap by mutableStateOf(false)
        private set

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnCompletionListener { onSegmentDone() }
        player.setOnErrorListener { _, _, _ ->
            // 坏文件不占着 1 秒空档，直接跳过
            onSegmentDone(instant = true)
            true
        }
    }

    /** 从第一段开始播一串；重复点同一段则暂停/继续 */
    fun play(keys: List<String>) {
        if (keys.isEmpty()) return
        if (keys.size == 1 && current == keys[0] && playing) {
            pause()
            return
        }
        wholeUnit = false
        // 词内三段（3 段）也按「一个词」对待，每词 N 遍同样生效
        wordCount = if (keys.size == 3) 1 else 0
        start(keys, 0)
    }

    /** 整单元连播；[start] 越界时从头开始，[words] 是其中词的个数（用于每词重复） */
    fun playFrom(keys: List<String>, start: Int, words: Int = 0) {
        if (keys.isEmpty()) return
        wholeUnit = true
        wordCount = words.coerceIn(0, keys.size / 3)
        start(keys, if (start in keys.indices) start else 0)
    }

    private fun start(keys: List<String>, at: Int) {
        queue = keys
        size = keys.size
        round = 0
        open(at, auto = false)
    }

    fun pause() {
        if (pendingNext >= 0) {
            // 正停在两段之间：撤销那一下，位置留在刚播完的段上
            handler.removeCallbacks(gapTick)
            playing = false
            inGap = false
            return
        }
        runCatching { if (player.isPlaying) player.pause() }
        playing = false
    }

    fun resume() {
        if (pendingNext >= 0) {
            playing = true
            advanceNow()
            return
        }
        if (current == null) return
        runCatching { player.start() }
        playing = true
    }

    fun toggle() {
        if (playing) pause() else resume()
    }

    /** 跳到队列里的第 [i] 段；整单元连播时清单点谁跳谁 */
    fun jumpTo(i: Int) {
        if (i in queue.indices) {
            round = 0
            open(i, auto = false)
        }
    }

    fun next() = jumpTo(index + 1)

    fun previous() = jumpTo(index - 1)

    fun stop() {
        handler.removeCallbacks(gapTick)
        pendingNext = -1
        round = 0
        queue = emptyList()
        pos = 0
        size = 0
        index = -1
        wholeUnit = false
        current = null
        playing = false
        inGap = false
        runCatching { if (player.isPlaying) player.stop() }
    }

    fun release() {
        handler.removeCallbacks(gapTick)
        playing = false
        current = null
        index = -1
        wholeUnit = false
        inGap = false
        runCatching { player.release() }
    }

    private fun open(i: Int, auto: Boolean) {
        val key = queue.getOrNull(i)
        if (key == null) {
            finish()
            return
        }
        handler.removeCallbacks(gapTick)
        pendingNext = -1
        inGap = false
        if (!auto) round = 0
        pos = i
        index = i
        current = key
        playing = true
        runCatching { onSegment?.invoke(key) }
        runCatching { onPosition?.invoke(i) }

        val ok = runCatching {
            player.reset()
            // 下载的单元在 filesDir 下，内置的在 assets 里；同 key 两处同名
            val file = ContentRepo.audioFile(context, key)
            if (file.exists()) {
                player.setDataSource(file.absolutePath)
            } else {
                context.assets.openFd(key).use { fd ->
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
            }
            player.prepare()
            player.start()
        }.isSuccess

        if (!ok) advanceNow()
    }

    /** 一段自然播完（或出错）：算出下一段，先空 [gapMs] 再开下一段 */
    private fun onSegmentDone(instant: Boolean = false) {
        val step = nextPlayStep(
            from = pos,
            size = queue.size,
            wordCount = wordCount,
            repeatTimes = wordRepeat,
            round = round,
            loop = loopUnit
        )
        if (step == null) {
            finish()
            return
        }
        round = step.second
        pendingNext = step.first
        playing = true
        val wait = if (instant) 0L else gapMs
        if (wait > 0) {
            inGap = true
            handler.postDelayed(gapTick, wait)
        } else {
            advanceNow()
        }
    }

    private fun advanceNow() {
        handler.removeCallbacks(gapTick)
        val next = pendingNext
        pendingNext = -1
        inGap = false
        if (next >= 0) open(next, auto = true) else finish()
    }

    private fun finish() {
        val wasWholeUnit = wholeUnit
        stop()
        if (wasWholeUnit) runCatching { onFinished?.invoke() }
    }
}
