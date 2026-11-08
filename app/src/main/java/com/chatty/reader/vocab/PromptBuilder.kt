package com.chatty.reader.vocab

/**
 * 把“固定要求 + 十个单词”拼成一段完整文本，交给 ChatGPT。
 *
 * 固定要求里可以写占位符 {words}，单词表会插到那个位置；
 * 没写占位符时，单词表追加在末尾 —— 这样用户随手改模板也不会丢词。
 */
object PromptBuilder {

    const val PLACEHOLDER = "{words}"

    /** 单词表，一行一个；“附带释义”时把音标与中文释义一起给 */
    fun wordBlock(words: List<Word>, withMeaning: Boolean): String =
        words.joinToString("\n") { w ->
            if (withMeaning) {
                val tail = buildString {
                    if (w.phonetic.isNotBlank()) append(" [").append(w.phonetic).append("]")
                    if (w.meaning.isNotBlank()) append("  ").append(w.meaning)
                }
                "${w.no}. ${w.word}$tail"
            } else {
                "${w.no}. ${w.word}"
            }
        }

    fun build(requirement: String, words: List<Word>, withMeaning: Boolean): String {
        val block = wordBlock(words, withMeaning)
        val template = requirement.trim()
        if (template.isEmpty()) return block
        return if (template.contains(PLACEHOLDER)) {
            template.replace(PLACEHOLDER, block)
        } else {
            block + "\n\n" + template
        }.trim()
    }
}
