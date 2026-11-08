package com.chatty.reader.launch

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object AppLauncher {

    /** 列出所有能从桌面启动的应用（不含自己） */
    fun listLaunchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val self = context.packageName

        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != self }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * 一次性启动多个应用。
     *
     * 不用循环调用 startActivity：Android 10 起禁止“后台启动界面”，
     * 拉起第一个应用后本应用就失去了可见窗口，第二次调用会被系统拦掉。
     * startActivities 属于同一次前台操作，不受这条限制，且不需要逐个加延迟。
     *
     * @param packages 按顺序启动，数组靠后的位于最上层
     */
    fun launchAll(context: Context, packages: List<String>): Boolean {
        val targets = packages.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty()) return false

        val intents = targets.mapNotNull { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        if (intents.isEmpty()) return false

        val batchOk = runCatching {
            context.startActivities(intents.toTypedArray())
            true
        }.getOrDefault(false)

        if (batchOk) return true

        // 个别 ROM 不支持批量启动，退化为逐个拉起
        var any = false
        intents.forEach { intent ->
            if (runCatching { context.startActivity(intent) }.isSuccess) any = true
        }
        return any
    }

    fun labelOf(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        }.getOrNull()
    }

    fun iconOf(context: Context, packageName: String): Drawable? {
        if (packageName.isBlank()) return null
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationIcon(packageName)
        }.getOrNull()
    }
}
