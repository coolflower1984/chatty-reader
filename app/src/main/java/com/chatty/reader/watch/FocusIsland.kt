package com.chatty.reader.watch

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * 小米「焦点通知 / 超级岛」适配。
 *
 * 澎湃 OS 把通知上岛做成了一项系统能力：应用在原生通知的 extras 里塞一段
 * `miui.focus.param` JSON，支持的机型就会把它渲染成状态栏焦点通知（OS2）
 * 或挖孔周围的超级岛（OS3）。
 *
 * 两个前提，缺一不可：
 *  1. 系统版本支持（`notification_focus_protocol` > 0）；
 *  2. 用户在系统设置里给本应用开了「焦点通知」权限。
 *
 * 实测经验：光有权限也可能只是状态栏出文字、挖孔那块不渲染 —— 小米对第三方
 * 应用还有一层平台侧白名单。所以这里全程 runCatching，任何一步失败都安静退回
 * 普通通知，不影响原有功能。
 */
object FocusIsland {

    private const val KEY_PARAM = "miui.focus.param"
    private const val KEY_ACTIONS = "miui.focus.actions"
    private const val KEY_ACTION_1 = "miui.focus.action_1"

    /** 0 = 不支持；1 = OS1 焦点通知；2 = OS2 焦点通知；3 = OS3 超级岛 */
    fun protocolVersion(context: Context): Int {
        val read = runCatching {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        }.getOrDefault(0)
        if (read > 0) return read
        // 个别机型把这个 key 设成受保护项，读出来是 0。
        // 那就退回系统属性判断 —— 有岛特性就按 OS3 处理。
        return if (supportsIsland()) 3 else 0
    }

    /** OS3 才有的挖孔岛 */
    fun supportsIsland(): Boolean = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
    }.getOrDefault(false)

    /** 用户在系统设置里的「焦点通知」开关 */
    fun hasPermission(context: Context): Boolean = runCatching {
        val uri = Uri.parse("content://miui.statusbar.notification.public")
        val extras = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(uri, "canShowFocus", null, extras)
            ?.getBoolean("canShowFocus", false) ?: false
    }.getOrDefault(false)

    /** 真的能把通知画到状态栏（再往上能不能进挖孔，看系统给不给） */
    fun available(context: Context): Boolean = protocolVersion(context) > 0 && hasPermission(context)

    /**
     * 给一条已经建好的通知补上岛参数。不支持的机型原样返回。
     *
     * [backIntent] 给了的话，岛上会多一个「回到本页」按钮 —— 挂在 `miui.focus.actions`
     * 上，点它直接回十词一组。点岛的其他区域走的还是通知自己的 contentIntent。
     */
    fun decorate(
        context: Context,
        notification: Notification,
        title: String,
        content: String,
        backIntent: android.app.PendingIntent? = null,
        backLabel: String = "回到本页",
        force: Boolean = false
    ) {
        // force：探测结果不可信时（查询失败、key 读不到），按 OS3 硬着头皮发一次，
        // 系统不收也只是退回普通通知
        val version = if (force) maxOf(protocolVersion(context), 3) else protocolVersion(context)
        if (version <= 0) return
        runCatching {
            val extras = notification.extras ?: Bundle()

            var hasAction = false
            if (backIntent != null) {
                val action = Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_revert),
                    backLabel,
                    backIntent
                ).build()
                extras.putBundle(KEY_ACTIONS, Bundle().apply {
                    putParcelable(KEY_ACTION_1, action)
                })
                hasAction = true
            }

            extras.putString(KEY_PARAM, build(version, title, content, hasAction))
            notification.extras = extras
        }
    }

    /**
     * 拼参数。
     *
     * `baseInfo` + `ticker` 是焦点通知的最小集，OS1/OS2 用这些就够；
     * OS3 再加 `param_island` 才有摘要态的大岛/小岛。
     */
    private fun build(version: Int, title: String, content: String, hasAction: Boolean): String {
        val v2 = JSONObject().apply {
            put("business", "chatty_reader")
            // 别每次更新都自动弹开，只在第一次出现时展开一下
            put("islandFirstFloat", true)
            put("enableFloat", false)
            // 持续性通知：PrepareService 会一直改它
            put("updatable", true)
            put("ticker", content)
            put("baseInfo", JSONObject().apply {
                put("type", 1)
                put("title", title)
                put("content", content)
            })
            if (hasAction) {
                put(
                    "actions",
                    JSONArray().put(JSONObject().put("action", KEY_ACTION_1))
                )
            }
        }

        if (version >= 3) {
            v2.put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                // 有些文档里这个字段叫 islandPriority，2 = 较高优先级，
                // 不写的话摘要态可能被别的通知挤掉
                put("islandPriority", 2)
                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("textInfo", JSONObject().apply {
                            put("title", title)
                            put("content", content)
                            put("showHighlightColor", true)
                        })
                    })
                })
                // 小岛留空，系统会兜底用应用图标
                put("smallIslandArea", JSONObject())
            })
        }

        return JSONObject().put("param_v2", v2).toString()
    }

    /** 设置页那一行要显示的结论 */
    enum class State {
        /** 这台机器没有焦点通知能力 */
        UNSUPPORTED,
        /** 支持，但用户没给本应用开权限 */
        NO_PERMISSION,
        /** 已授权，能显示在状态栏 */
        READY
    }

    fun state(context: Context): State = when {
        protocolVersion(context) <= 0 -> State.UNSUPPORTED
        !hasPermission(context) -> State.NO_PERMISSION
        else -> State.READY
    }

    /** 给设置页看的原始读数，出问题时一眼能对上到底卡在哪 */
    fun diagnose(context: Context): String {
        val version = protocolVersion(context)
        val perm = hasPermission(context)
        val island = supportsIsland()
        return "协议 $version · 权限${if (perm) "已开" else "关闭"} · 岛${if (island) "支持" else "不支持"}"
    }

    /** 跳到本应用的通知设置页，让用户去开焦点通知 */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
