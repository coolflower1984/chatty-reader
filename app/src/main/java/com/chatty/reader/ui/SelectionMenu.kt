package com.chatty.reader.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatty.reader.share.AskAi
import com.chatty.reader.study.VocabBook
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 一次选区的上下文：从哪一段选的、这条段落属于哪个单元、怎么把字取出来。
 *
 * 拿选中文字靠的是「先让系统复制，再读剪贴板」——Compose 没有公开获取选区的接口，
 * 而 showMenu 给的 onCopyRequested 正好能把当前选区写进剪贴板。
 */
data class SelectionPayload(
    val paragraph: String,
    val unitId: Int,
    val copy: (() -> Unit)?,
    val dismiss: () -> Unit
)

/** 每个段落一个，知道自己是哪一段，选区菜单才能带上语境 */
private class ParagraphToolbar(
    private val paragraph: String,
    private val unitId: Int,
    private val onShow: (SelectionPayload) -> Unit,
    private val onHide: () -> Unit
) : TextToolbar {

    private var shown = false

    override val status: TextToolbarStatus
        get() = if (shown) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        shown = true
        onShow(
            SelectionPayload(
                paragraph = paragraph,
                unitId = unitId,
                copy = onCopyRequested,
                dismiss = ::hide
            )
        )
    }

    override fun hide() {
        shown = false
        onHide()
    }
}

/**
 * 可选中的正文。长按选词 / 拖动手柄选句，系统工具栏被换成页面底部的动作条。
 */
@Composable
fun SelectableText(
    text: String,
    unitId: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontStyle: FontStyle? = null,
    onSelection: (SelectionPayload?) -> Unit
) {
    val currentShow by rememberUpdatedState(onSelection)
    val currentHide by rememberUpdatedState { onSelection(null) }
    val toolbar = remember(text, unitId) {
        ParagraphToolbar(
            paragraph = text,
            unitId = unitId,
            onShow = { currentShow(it) },
            onHide = { currentHide() }
        )
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer {
            Text(
                text = text,
                modifier = modifier,
                style = style,
                color = color,
                fontStyle = fontStyle
            )
        }
    }
}

/**
 * 底部的选区动作条：存生词本、丢给 DeepSeek、复制。
 */
@Composable
fun SelectionActionBar(
    payload: SelectionPayload,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    fun grab(): String {
        val copy = payload.copy ?: return ""
        runCatching { copy() }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
    }

    fun kindOf(s: String): String {
        val words = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size >= 4) "sentence" else "word"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "「${payload.paragraph.trim().take(48)}${if (payload.paragraph.trim().length > 48) "…" else ""}」",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val picked = grab()
                        if (picked.isBlank()) {
                            Toast.makeText(context, "没拿到选中的文字，再选一次", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val added = VocabBook.add(
                            context = context,
                            text = picked,
                            kind = kindOf(picked),
                            unitId = payload.unitId,
                            contextText = payload.paragraph
                        )
                        Toast.makeText(
                            context,
                            if (added) "已加入生词本" else "生词本里已经有这条了",
                            Toast.LENGTH_SHORT
                        ).show()
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                ) { Text("＋ 生词本", style = MaterialTheme.typography.labelMedium) }

                Button(
                    onClick = {
                        val picked = grab()
                        if (picked.isBlank()) {
                            Toast.makeText(context, "没拿到选中的文字，再选一次", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val outcome = AskAi.send(
                            context = context,
                            text = picked,
                            kind = kindOf(picked),
                            unitId = payload.unitId,
                            contextText = payload.paragraph
                        )
                        Toast.makeText(
                            context,
                            when (outcome) {
                                AskAi.Outcome.SENT -> "已送到 DeepSeek，按发送即可"
                                AskAi.Outcome.CHOOSER -> "选一个 AI 应用，文字已复制"
                                AskAi.Outcome.FAILED -> "没找到能接收文字的应用"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                ) { Text("问 DeepSeek", style = MaterialTheme.typography.labelMedium) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val picked = grab()
                        Toast.makeText(
                            context,
                            if (picked.isBlank()) "没拿到选中的文字" else "已复制",
                            Toast.LENGTH_SHORT
                        ).show()
                        onClose()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("复制", style = MaterialTheme.typography.labelSmall) }

                Text(
                    "拖动两端手柄可以扩到整句",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("关闭", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
