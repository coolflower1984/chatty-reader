package com.chatty.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * 全部可配置项。字段用 Compose 状态承载，界面读到变化会自动重组；
 * 每次赋值同步落盘到 SharedPreferences。
 */
class AppConfig(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("chatty_prefs", Context.MODE_PRIVATE)

    // ---------- 当前词组 ----------

    private val _listNo = mutableIntStateOf(sp.getInt(K_LIST, 1))
    var listNo: Int
        get() = _listNo.intValue
        set(v) {
            _listNo.intValue = v
            sp.edit().putInt(K_LIST, v).apply()
        }

    private val _groupNo = mutableIntStateOf(sp.getInt(K_GROUP, 1))
    var groupNo: Int
        get() = _groupNo.intValue
        set(v) {
            _groupNo.intValue = v
            sp.edit().putInt(K_GROUP, v).apply()
        }

    // ---------- 阅读字号 ----------

    /** 正文缩放系数，1.0 是原始大小。上下限卡死，免得手滑调成看不见 */
    private val _fontScale = mutableFloatStateOf(
        sp.getFloat(K_FONT_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
    )
    var fontScale: Float
        get() = _fontScale.floatValue
        set(v) {
            val safe = v.coerceIn(MIN_SCALE, MAX_SCALE)
            _fontScale.floatValue = safe
            sp.edit().putFloat(K_FONT_SCALE, safe).apply()
        }

    // ---------- 固定要求 ----------

    private val _requirement = mutableStateOf(
        sp.getString(K_REQUIREMENT, null)
            ?.takeUnless { isLegacyRequirement(it) }
            ?: DEFAULT_REQUIREMENT
    )
    var requirement: String
        get() = _requirement.value
        set(v) {
            _requirement.value = v
            sp.edit().putString(K_REQUIREMENT, v).apply()
        }

    fun resetRequirement() {
        requirement = DEFAULT_REQUIREMENT
    }

    /** 老版本留下的默认文案，升级时自动换成新模板；用户自己改过的不动 */
    private fun isLegacyRequirement(text: String): Boolean =
        text.contains("Please help me learn them in English context") ||
            text.contains("请帮我处理下面这段内容")

    // ---------- 准备阶段 ----------

    /** 常驻一条通知把进程抬为前台，降低被系统清掉的概率 */
    private val _keepAlive = mutableStateOf(sp.getBoolean(K_KEEP_ALIVE, true))
    var keepAlive: Boolean
        get() = _keepAlive.value
        set(v) {
            _keepAlive.value = v
            sp.edit().putBoolean(K_KEEP_ALIVE, v).apply()
        }

    /**
     * 检测到你停在 X 里这么多秒后，自动把你送回本页。
     * 刷新本身没法感知，用停留时长近似 —— 够你拉一次并扫两眼。
     */
    private val _xDwell = mutableIntStateOf(sp.getInt(K_X_DWELL, 10).coerceIn(3, 30))
    var xDwellSeconds: Int
        get() = _xDwell.intValue
        set(v) {
            val safe = v.coerceIn(3, 30)
            _xDwell.intValue = safe
            sp.edit().putInt(K_X_DWELL, safe).apply()
        }

    /**
     * 检测到你停在代理工具里这么多秒后，自动把你送回本页。
     * 这段时间就是留给你点开关的：够点一下就够，太长反而等得烦。
     */
    private val _clashDwell = mutableIntStateOf(sp.getInt(K_CLASH_DWELL, 3).coerceIn(1, 15))
    var clashDwellSeconds: Int
        get() = _clashDwell.intValue
        set(v) {
            val safe = v.coerceIn(1, 15)
            _clashDwell.intValue = safe
            sp.edit().putInt(K_CLASH_DWELL, safe).apply()
        }

    private val _withMeaning = mutableStateOf(sp.getBoolean(K_WITH_MEANING, false))
    var withMeaning: Boolean
        get() = _withMeaning.value
        set(v) {
            _withMeaning.value = v
            sp.edit().putBoolean(K_WITH_MEANING, v).apply()
        }

    /**
     * 忽略权限探测，无条件带上焦点通知参数。
     *
     * 有些机型把 `canShowFocus` 查不到、或者 `notification_focus_protocol` 是受保护项，
     * 探测会误判成「不支持」，于是连参数都不带。打开这个开关就跳过判断，
     * 让系统自己去决定收不收 —— 收下就上，不收也只是条普通通知。
     */
    private val _islandForce = mutableStateOf(sp.getBoolean(K_ISLAND_FORCE, false))
    var islandForce: Boolean
        get() = _islandForce.value
        set(v) {
            _islandForce.value = v
            sp.edit().putBoolean(K_ISLAND_FORCE, v).apply()
        }

    // ---------- 目标应用 ----------

    /**
     * 启动时自动跳到代理工具。默认关闭 —— 一打开就被拽走太干扰。
     * 只认「用户在新版里主动开过」这一种 true：老版本存下的 true 会被迁移标记挡掉，升级后自动回到关闭。
     */
    private val _autoOpenClash = mutableStateOf(
        sp.getBoolean(K_AUTO_CLASH, false) && sp.getBoolean(K_AUTO_CLASH_MIGRATED, false)
    )
    var autoOpenClash: Boolean
        get() = _autoOpenClash.value
        set(v) {
            _autoOpenClash.value = v
            sp.edit()
                .putBoolean(K_AUTO_CLASH, v)
                .putBoolean(K_AUTO_CLASH_MIGRATED, true)
                .apply()
        }

    private val _clashPkg = mutableStateOf(sp.getString(K_CLASH, "").orEmpty())
    var clashPkg: String
        get() = _clashPkg.value
        set(v) {
            _clashPkg.value = v
            sp.edit().putString(K_CLASH, v).apply()
        }

    private val _xPkg = mutableStateOf(sp.getString(K_X, "").orEmpty())
    var xPkg: String
        get() = _xPkg.value
        set(v) {
            _xPkg.value = v
            sp.edit().putString(K_X, v).apply()
        }

    private val _gptPkg = mutableStateOf(sp.getString(K_GPT, "").orEmpty())
    var gptPkg: String
        get() = _gptPkg.value
        set(v) {
            _gptPkg.value = v
            sp.edit().putString(K_GPT, v).apply()
        }

    companion object {
        const val K_LIST = "list_no"
        const val K_GROUP = "group_no"
        const val K_REQUIREMENT = "requirement"
        const val K_WITH_MEANING = "with_meaning"
        const val K_AUTO_CLASH = "auto_clash"
        const val K_AUTO_CLASH_MIGRATED = "auto_clash_migrated_v24"
        const val K_CLASH = "clash_pkg"
        const val K_X = "x_pkg"
        const val K_GPT = "gpt_pkg"
        const val K_X_DWELL = "x_dwell"
        const val K_CLASH_DWELL = "clash_dwell"
        const val K_ISLAND_FORCE = "island_force"
        const val K_KEEP_ALIVE = "keep_alive_pref"
        const val K_FONT_SCALE = "reader_font_scale"

        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.5f

        val DEFAULT_REQUIREMENT = """
            I'm preparing for CET-6. Here are 10 words from my list:

            {words}

            Work through them ONE BY ONE in the order above. For every word, use exactly this structure:

            **1. word**
            - Guess: 2-3 short sentences in simple English (A2-B1). The meaning should be guessable from the context, but never translate the word or define it directly here.
            - Meaning: one brief English definition, then a Chinese gloss in brackets.
            - Context: a 2-3 sentence paragraph at CET-6 level that uses the word naturally.

            Rules for the word section:
            - Cover all 10 words, in my order. Do not skip, merge or reorder them.
            - "Guess" must stay genuinely simple: short sentences, common words.
            - "Context" must be noticeably harder than "Guess".

            After the last word, add:

            ## Reading Comprehension
            - One passage of 150-200 words at CET-6 level that naturally uses at least 7 of the 10 words.
            - 5 multiple-choice questions (A/B/C/D) about the passage.
            - The answer key at the very end, clearly separated from the questions.

            Write everything in English, except the single Chinese gloss in "Meaning".
        """.trimIndent()
    }
}
