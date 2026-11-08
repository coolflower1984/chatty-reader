package com.chatty.reader.watch

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * 读取“此刻哪个应用在前台”。
 *
 * 这里用的是系统的使用情况记录（UsageStats），不是无障碍 —— 只能知道应用之间的切换，
 * 看不到应用内部的点击。所以“你刷新完了没有”没法精确判断，用停留时长来近似。
 *
 * 需要用户在系统设置里单独授予「使用情况访问权限」，应用无法自行开启。
 */
object UsageProbe {

    /** 是否已经有使用情况访问权限 */
    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** 跳到系统的「使用情况访问权限」列表 */
    fun requestAccess(context: Context) {
        // 部分 ROM 不认带包名的 intent，退回到列表页让用户自己找
        val withPkg = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (runCatching { context.startActivity(withPkg) }.isFailure) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * 当前前台应用的包名。查不到时返回 null（多半是权限没给）。
     *
     * 取最近 15 秒内最后一次“进入前台”的记录：应用切换时系统会写一条事件，
     * 桌面、通知栏下拉这类瞬态不会产生误导性的结果。
     */
    @SuppressLint("DefaultLocale")
    fun foreground(context: Context): String? = runCatching {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 15_000L, end) ?: return null

        var pkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND 的值与 API 29 的 ACTIVITY_RESUMED 相同，用它兼容旧系统
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                pkg = event.packageName
            }
        }
        pkg
    }.getOrNull()
}
