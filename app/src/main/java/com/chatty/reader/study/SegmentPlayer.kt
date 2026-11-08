package com.chatty.reader.study

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 播放一段或一串 assets 里的 mp3。
 *
 * 只用 MediaPlayer 一个实例，靠 reset 换源 —— 一轮朗读有几十段，
 * 每段新建一个实例会在低端机上攒出一堆 GC 压力。
 */
class SegmentPlayer(
    private val context: Context,
    /** 每开一段就回调一次，用来记学习进度；连播时后续片段也会走到这里 */
    // 这里必须写全限定名：同包下有个 study.Unit，裸写 Unit 会被当成那个类
    private val onSegment: ((String) -> kotlin.Unit)? = null
) {

    private val player = MediaPlayer()
    private var queue: List<String> = emptyList()
    private var pos = 0

    /** 当前正在播（或暂停在）哪一段；null 表示空闲 */
    var current by mutableStateOf<String?>(null)
        private set

    var playing by mutableStateOf(false)
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
        queue = keys
        pos = 0
        open(0)
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

    fun stop() {
        queue = emptyList()
        pos = 0
        current = null
        playing = false
        runCatching { if (player.isPlaying) player.stop() }
    }

    fun release() {
        playing = false
        current = null
        runCatching { player.release() }
    }

    private fun open(index: Int) {
        val key = queue.getOrNull(index)
        if (key == null) {
            stop()
            return
        }
        pos = index
        current = key
        playing = true
        runCatching { onSegment?.invoke(key) }

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
        if (next < queue.size) open(next) else stop()
    }
}
