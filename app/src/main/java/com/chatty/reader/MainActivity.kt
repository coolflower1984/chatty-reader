package com.chatty.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chatty.reader.ui.AppPickerScreen
import com.chatty.reader.ui.ChattyTheme
import com.chatty.reader.ui.HomeScreen
import com.chatty.reader.ui.LibraryScreen
import com.chatty.reader.ui.ReaderScreen
import com.chatty.reader.study.ApkUpdate
import com.chatty.reader.study.UpdateCenter
import com.chatty.reader.study.UpdateRepo
import com.chatty.reader.ui.SettingsScreen
import com.chatty.reader.ui.UpdateAvailableDialog
import com.chatty.reader.ui.VocabScreen

class MainActivity : ComponentActivity() {

    /** 被 ChainService 从 X 拉回来时置真，界面据此回到首页 */
    private var broughtBack by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val freshLaunch = savedInstanceState == null && launchedFromHome()
        if (intent?.getBooleanExtra(EXTRA_BROUGHT_BACK, false) == true) broughtBack = true

        askNotificationPermission()

        setContent {
            ChattyTheme {
                val context = LocalContext.current
                val state = remember { AppState(context.applicationContext) }
                val lifecycleOwner = LocalLifecycleOwner.current

                val online by UpdateCenter.state.collectAsState()
                var updatePrompt by remember { mutableStateOf<UpdateRepo.Manifest?>(null) }

                LaunchedEffect(Unit) {
                    state.load()
                    state.onForeground()
                    state.syncKeepAlive()
                    if (freshLaunch) state.autoOpenClashIfNeeded()
                    // 打开就查一次；同一进程里十分钟内不重复打服务器
                    UpdateCenter.autoCheck(context)
                }

                // 清单回来了再决定要不要问：有新版、且用户没跳过这个版本
                LaunchedEffect(online.manifest) {
                    val m = online.manifest ?: return@LaunchedEffect
                    if (ApkUpdate.askedThisRun) return@LaunchedEffect
                    if (ApkUpdate.shouldPrompt(context, m, BuildConfig.VERSION_CODE)) {
                        updatePrompt = m
                    }
                }

                // 从代理工具 / 确认应用返回时，把刚走出去的那一步自动打勾
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) state.onForeground()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        state.dispose()
                    }
                }

                // 从 X 被拉回来：无论当时停在哪个子页，都回到首页，方便直接发送
                LaunchedEffect(broughtBack) {
                    if (broughtBack) {
                        state.screen = Screen.HOME
                        broughtBack = false
                    }
                }

                BackHandler(enabled = state.screen != Screen.HOME) {
                    state.screen = when (state.screen) {
                        Screen.READER -> Screen.LIBRARY
                        Screen.VOCAB -> state.vocabReturn
                        Screen.PICKER -> Screen.SETTINGS
                        else -> Screen.HOME
                    }
                }

                updatePrompt?.let { m ->
                    UpdateAvailableDialog(
                        manifest = m,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onUpdate = {
                            if (!ApkUpdate.openDownload(context, m.apkFile)) {
                                Toast.makeText(
                                    context,
                                    "没找到浏览器，请手动打开下载页",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            ApkUpdate.askedThisRun = true
                            updatePrompt = null
                        },
                        onLater = {
                            ApkUpdate.askedThisRun = true
                            updatePrompt = null
                        },
                        onSkip = {
                            ApkUpdate.skipVersion(context, m.apkVersionCode)
                            ApkUpdate.askedThisRun = true
                            updatePrompt = null
                        }
                    )
                }

                when (state.screen) {
                    Screen.HOME -> HomeScreen(
                        state = state,
                        onOpenSettings = { state.screen = Screen.SETTINGS },
                        onOpenLibrary = { state.screen = Screen.LIBRARY },
                        onOpenVocab = {
                            state.vocabReturn = Screen.HOME
                            state.screen = Screen.VOCAB
                        }
                    )

                    Screen.LIBRARY -> LibraryScreen(
                        onOpen = { state.openUnit(it) },
                        onBack = { state.screen = Screen.HOME }
                    )

                    Screen.READER -> {
                        val unit = state.currentUnit
                        if (unit == null) {
                            LibraryScreen(
                                onOpen = { state.openUnit(it) },
                                onBack = { state.screen = Screen.HOME }
                            )
                        } else {
                            ReaderScreen(
                                unit = unit,
                                state = state,
                                onBack = { state.screen = Screen.LIBRARY },
                                onOpenVocab = {
                                    state.vocabReturn = Screen.READER
                                    state.screen = Screen.VOCAB
                                }
                            )
                        }
                    }

                    Screen.VOCAB -> VocabScreen(
                        onBack = { state.screen = state.vocabReturn },
                        onOpenUnit = { state.openUnit(it) }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        state = state,
                        onBack = { state.screen = Screen.HOME }
                    )

                    Screen.PICKER -> AppPickerScreen(
                        state = state,
                        onBack = { state.screen = Screen.SETTINGS }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_BROUGHT_BACK, false)) broughtBack = true
    }

    /** 安卓 13 起通知要单独授权；没给也不影响功能，只是看不到那条进度通知 */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    /** 从最近任务列表恢复时不要重复拉起代理工具 */
    private fun launchedFromHome(): Boolean {
        val flags = intent?.flags ?: 0
        return flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
    }

    companion object {
        const val EXTRA_BROUGHT_BACK = "brought_back"
    }
}
