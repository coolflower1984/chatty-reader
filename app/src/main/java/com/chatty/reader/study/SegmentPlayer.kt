package com.chatty.reader.study

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
 * 队列有两种用法：点单段 / 词内三段用 [play]，整单元连播用 [playFrom]，
 * 后者会把 [wholeUnit] 置 true，界面据此决定要不要显示连播条。
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
    private var queue: List<String> = emptyList()
    private var pos = 0

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

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnCompletionListener { advance() }
        player.setOnErrorListener { _, _, _ ->
            advance()
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
        start(keys, 0)
    }

    /** 整单元连播；[start] 越界时从头开始 */
    fun playFrom(keys: List<String>, start: Int) {
        if (keys.isEmpty()) return
        wholeUnit = true
        start(keys, if (start in keys.indices) start else 0)
    }

    private fun start(keys: List<String>, at: Int) {
        queue = keys
        size = keys.size
        open(at)
    }

    fun pause() {
        runCatching { if (player.isPlaying) player.pause() }
        playing = false
    }

    fun resume() {
        if (current == null) return
        runCatching { player.start() }
        playing = true
    }

    fun toggle() {
        if (playing) pause() else resume()
    }

    /** 跳到队列里的第 [i] 段；整单元连播时清单点谁跳谁 */
    fun jumpTo(i: Int) {
        if (i in queue.indices) open(i)
    }

    fun next() = jumpTo(index + 1)

    fun previous() = jumpTo(index - 1)

    fun stop() {
        queue = emptyList()
        pos = 0
        size = 0
        index = -1
        wholeUnit = false
        current = null
        playing = false
        runCatching { if (player.isPlaying) player.stop() }
    }

    fun release() {
        playing = false
        current = null
        index = -1
        wholeUnit = false
        runCatching { player.release() }
    }

    private fun open(i: Int) {
        val key = queue.getOrNull(i)
        if (key == null) {
            stop()
            return
        }
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

        if (!ok) advance()
    }

    private fun advance() {
        val next = pos + 1
        if (next < queue.size) {
            open(next)
        } else {
            val wasWholeUnit = wholeUnit
            stop()
            if (wasWholeUnit) runCatching { onFinished?.invoke() }
        }
    }
}
