package com.chatty.reader.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter

/**
 * 把一个 android Drawable 画到 Compose 里。
 * 应用图标多为 AdaptiveIconDrawable（intrinsicWidth 为 -1），所以不能用
 * BitmapPainter，直接在 DrawScope 上绘制最稳。
 */
private class DrawablePainter(private val drawable: Drawable) : Painter() {

    override val intrinsicSize: Size
        get() {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
            return Size(w.toFloat(), h.toFloat())
        }

    override fun DrawScope.onDraw() {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.draw(drawContext.canvas.nativeCanvas)
    }
}

@Composable
fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) return
    val painter = remember(drawable) { DrawablePainter(drawable) }
    Image(painter = painter, contentDescription = null, modifier = modifier)
}
