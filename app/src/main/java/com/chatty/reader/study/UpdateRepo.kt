package com.chatty.reader.study

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 在线资源：拉更新清单、下更新包。
 *
 * 站点是同一台机器上的静态目录，全部文件都要求带 sharecode，
 * 少一个参数就是 403 —— 所以 URL 统一从这里拼，别在别处手写。
 */
object UpdateRepo {

    private const val BASE = "https://a2ca408427b7b4aeb.app.workbuddy.host"
    private const val SHARE = "F32OK2QEQP"

    data class Pack(
        val file: String,
        val from: Int,
        val to: Int,
        val units: Int,
        val bytes: Long
    ) {
        val title: String get() = "Unit $from–$to"
        val mb: String get() = "%.1f".format(bytes / 1024f / 1024f)
    }

    data class Manifest(
        val totalUnits: Int,
        val builtIn: Int,
        val packs: List<Pack>,
        val apkVersionCode: Int = 0,
        val apkVersionName: String = "",
        val apkFile: String = "",
        val apkBytes: Long = 0L,
        val apkNotes: String = ""
    ) {
        fun packOf(id: Int): Pack? = packs.firstOrNull { id in it.from..it.to }
    }

    fun manifestUrl(): String = "$BASE/manifest.json?sharecode=$SHARE"

    fun packUrl(file: String): String = "$BASE/packs/$file?sharecode=$SHARE"

    fun apkUrl(file: String): String = "$BASE/$file?sharecode=$SHARE"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ChattyReader/${com.chatty.reader.BuildConfig.VERSION_NAME} (Android)")
            setRequestProperty("Accept-Encoding", "identity")
        }

    /** 读清单。异常都在这里翻译成人话，界面直接拿去显示。 */
    fun fetchManifest(): Manifest {
        val conn = open(manifestUrl())
        try {
            val code = conn.responseCode
            if (code != 200) {
                throw IllegalStateException(
                    if (code == 403) "下载页拒绝访问（sharecode 失效？）" else "服务器返回 $code"
                )
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val arr = root.getJSONArray("packs")
            val packs = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Pack(
                            file = o.getString("file"),
                            from = o.getInt("from"),
                            to = o.getInt("to"),
                            units = o.optInt("units", o.getInt("to") - o.getInt("from") + 1),
                            bytes = o.optLong("bytes", 0L)
                        )
                    )
                }
            }
            val apk = root.optJSONObject("apk")
            return Manifest(
                totalUnits = root.optInt("totalUnits", 240),
                builtIn = root.optInt("builtIn", 10),
                packs = packs.sortedBy { it.from },
                apkVersionCode = apk?.optInt("versionCode", 0) ?: 0,
                apkVersionName = apk?.optString("versionName").orEmpty(),
                apkFile = apk?.optString("file").orEmpty(),
                apkBytes = apk?.optLong("bytes", 0L) ?: 0L,
                apkNotes = apk?.optString("notes").orEmpty()
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 下载一个包到 cache。返回的文件在导入后要立刻删掉 —— 一个包 30 多 MB，
     * 攒着会把手机空间吃光。
     */
    fun downloadPack(
        context: Context,
        pack: Pack,
        onProgress: (Long, Long) -> kotlin.Unit,
        isActive: () -> Boolean
    ): File {
        val dir = File(context.cacheDir, "packs").apply { mkdirs() }
        val out = File(dir, pack.file)
        out.delete()

        // 解压时 zip 和解压目录要同时存在，峰值差不多是包的两倍，留点余量
        if (pack.bytes > 0 && dir.usableSpace < pack.bytes * 2.2) {
            throw IllegalStateException(
                "存储空间不够（剩 %d MB，这个包要 %d MB）"
                    .format(dir.usableSpace / 1024 / 1024, pack.bytes * 2 / 1024 / 1024)
            )
        }

        val conn = open(packUrl(pack.file))
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("下载失败（HTTP $code）")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: pack.bytes

            conn.inputStream.use { input ->
                out.outputStream().use { sink ->
                    val buf = ByteArray(128 * 1024)
                    var got = 0L
                    var lastTick = 0L
                    while (true) {
                        if (!isActive()) throw IllegalStateException("已取消")
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        got += n
                        // 每秒刷几次就够，刷太密反而卡界面
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 150) {
                            lastTick = now
                            onProgress(got, total)
                        }
                    }
                    sink.flush()
                    onProgress(got, total)
                }
            }
        } catch (e: Exception) {
            // 取消或断线留下的半截文件没有任何用处，占着几十 MB 更碍事
            out.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        // 大小对不上说明传到一半断了，宁可重来也别喂给解压器
        if (pack.bytes > 0 && out.length() != pack.bytes) {
            val got = out.length()
            out.delete()
            throw IllegalStateException("包不完整（%d/%d 字节）".format(got, pack.bytes))
        }
        return out
    }
}

/**
 * 下载中心。状态放在单例里，界面来回切也不打断下载 ——
 * 23 个包在手机网络上要跑十几分钟，绑在页面上等于强迫用户盯着。
 */
object UpdateCenter {

    data class State(
        val checking: Boolean = false,
        val manifest: UpdateRepo.Manifest? = null,
        val checkError: String? = null,
        val checkedOnce: Boolean = false,
        val downloading: Boolean = false,
        val currentTitle: String = "",
        val currentPct: Int = 0,
        val donePacks: Int = 0,
        val totalPacks: Int = 0,
        val totalBytes: Long = 0L,
        val failed: Int = 0,
        val lastError: String? = null,
        val message: String? = null,
        val importedUnits: Int = 0
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** 上次拉清单的时间。冷启动自动检查靠它做节流，免得每次回到前台都打一次服务器 */
    @Volatile
    private var lastCheckAt = 0L

    /** 每次 start / cancel 都 +1。旧协程看到号码变了就闭嘴，免得把新下载的进度覆盖掉 */
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    /** 本地已有哪些单元，用来判断哪些包还没装 */
    fun missing(context: Context, manifest: UpdateRepo.Manifest): List<UpdateRepo.Pack> {
        val have = UnitStore.listIds(context).toSet()
        return manifest.packs.filter { p -> (p.from..p.to).any { it !in have } }
    }

    /** 冷启动用：同一个进程里十分钟内查过就不再查 */
    fun autoCheck(context: Context, minGapMs: Long = 10 * 60_000L) {
        if (System.currentTimeMillis() - lastCheckAt < minGapMs) return
        check(context)
    }

    fun check(context: Context) {
        if (_state.value.checking || _state.value.downloading) return
        lastCheckAt = System.currentTimeMillis()
        _state.value = _state.value.copy(checking = true, checkError = null)
        scope.launch {
            val result = runCatching { UpdateRepo.fetchManifest() }
            _state.value = result.fold(
                onSuccess = { m ->
                    _state.value.copy(
                        checking = false,
                        manifest = m,
                        checkedOnce = true,
                        checkError = null
                    )
                },
                onFailure = { e ->
                    _state.value.copy(
                        checking = false,
                        checkedOnce = true,
                        checkError = friendly(e)
                    )
                }
            )
        }
    }

    fun start(context: Context, packs: List<UpdateRepo.Pack>) {
        if (packs.isEmpty() || _state.value.downloading) return
        val app = context.applicationContext
        val my = generation.incrementAndGet()
        _state.value = _state.value.copy(
            downloading = true,
            donePacks = 0,
            totalPacks = packs.size,
            totalBytes = packs.sumOf { it.bytes },
            failed = 0,
            lastError = null,
            importedUnits = 0,
            currentTitle = "",
            currentPct = 0,
            message = null
        )
        job = scope.launch {
            var done = 0
            var failed = 0
            var units = 0
            for ((index, pack) in packs.withIndex()) {
                if (!isActive || my != generation.get()) break
                _state.value = _state.value.copy(
                    currentTitle = pack.title,
                    currentPct = 0,
                    donePacks = index
                )
                val one = runCatching {
                    val file = UpdateRepo.downloadPack(
                        context = app,
                        pack = pack,
                        onProgress = { got, total ->
                            if (my == generation.get()) {
                                val pct = if (total > 0) ((got * 100) / total).toInt().coerceIn(0, 100) else 0
                                _state.value = _state.value.copy(currentPct = pct)
                            }
                        },
                        isActive = { isActive }
                    )
                    val r = ContentRepo.importFile(app, file)
                    file.delete()
                    r
                }.getOrElse { ContentRepo.Result(false, emptyList(), friendly(it)) }

                if (one.ok) {
                    done++
                    units += one.added.size
                } else {
                    failed++
                    _state.value = _state.value.copy(lastError = one.message)
                }
                if (my == generation.get()) {
                    _state.value = _state.value.copy(donePacks = done, failed = failed, importedUnits = units)
                }
            }
            // 用户点了取消：状态已由 cancel() 收拾干净，别再覆盖成「完成 0 个包」
            if (!isActive || my != generation.get()) return@launch
            _state.value = _state.value.copy(
                downloading = false,
                currentTitle = "",
                currentPct = 0,
                message = when {
                    failed == 0 -> "已下载 $done 个包，共 $units 个单元"
                    done == 0 -> "下载中断：$failed 个包没成功，检查网络后重试"
                    else -> "完成 $done 个包，$failed 个失败，可以再点一次重试"
                }
            )
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        job = null
        _state.value = _state.value.copy(downloading = false, currentTitle = "", currentPct = 0)
    }

    fun consumeMessage() {
        if (_state.value.message != null) _state.value = _state.value.copy(message = null)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is UnknownHostException -> "连不上服务器，检查网络"
        is SocketTimeoutException -> "服务器没响应，稍后再试"
        else -> e.message ?: "网络出错"
    }
}
