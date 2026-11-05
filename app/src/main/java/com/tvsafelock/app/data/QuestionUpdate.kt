package com.tvsafelock.app.data

import android.content.Context
import com.tvsafelock.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 在线题库更新。
 *
 * 设计原则（电视盒子场景很重要）：
 *  1. **离线优先**：内置题库永远在 assets 里，网络断了 / 下到坏文件都自动回退，绝不会没题可出。
 *  2. **只认自己仓库**：地址写死在 BuildConfig.QUESTION_BANK_URL（由 GitHub Actions 打包时注入），
 *     界面上不提供改地址的输入框 —— 遥控器输 URL 太痛苦，也避免孩子乱填。
 *  3. **先验后换**：下载 → 逐题校验 → 写临时文件 → 再校验 → 才替换正式文件。
 *     任何一步失败都保留旧题库，不会出现「更新完一道题都没有」。
 *  4. **版本控制**：题库 JSON 必须有 version，且比本地大才替换，避免重复下载和来回覆盖。
 */
object QuestionUpdate {

    const val BANK_FILE = "questions_online.json"

    /** 题库元信息 */
    data class Meta(val version: Int, val count: Int, val updated: String)

    /** 一次更新的结果，直接拿去界面上显示 */
    data class Result(
        val ok: Boolean,
        val message: String,
        val version: Int = 0,
        val count: Int = 0
    )

    /** 打包时注入的主地址（GitHub raw）；本地没配置就是空串，界面会提示怎么配 */
    fun url(): String = BuildConfig.QUESTION_BANK_URL.trim()

    /** 备用地址（jsDelivr CDN）。国内电视访问 raw.githubusercontent.com 经常不通，这个能救场 */
    fun mirrorUrl(): String = BuildConfig.QUESTION_BANK_URL_MIRROR.trim()

    private fun candidates(): List<String> =
        listOf(url(), mirrorUrl()).filter { it.startsWith("https://", ignoreCase = true) }

    fun isConfigured(): Boolean = candidates().isNotEmpty()

    fun bankFile(context: Context): File = File(context.applicationContext.filesDir, BANK_FILE)

    /**
     * 严格校验题库 JSON。
     * 返回 null 表示这份题库不可用 —— 宁可继续用旧的，也不能让答题页崩掉或出一道没答案的题。
     */
    fun validate(text: String): Meta? = try {
        val root = JSONObject(text)
        val version = root.optInt("version", 0)
        if (version <= 0) null else {
            var count = 0
            var valid = true
            for (subject in listOf(Prefs.SUBJECT_CHINESE, Prefs.SUBJECT_ENGLISH)) {
                val arr = root.optJSONArray(subject)
                if (arr == null || arr.length() == 0) {
                    valid = false
                    break
                }
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o == null) { valid = false; break }
                    if (o.optString("q", "").isBlank()) { valid = false; break }
                    val grade = o.optInt("g", 0)
                    if (grade !in 1..6) { valid = false; break }
                    val opts = o.optJSONArray("o")
                    if (opts == null || opts.length() < 2) { valid = false; break }
                    val seen = HashSet<String>()
                    for (j in 0 until opts.length()) {
                        val s = opts.optString(j, "")
                        if (s.isBlank() || !seen.add(s)) { valid = false; break }
                    }
                    if (!valid) break
                    val answer = o.optInt("a", -1)
                    if (answer !in 0 until opts.length()) { valid = false; break }
                    count++
                }
                if (!valid) break
            }
            if (valid && count > 0) Meta(version, count, root.optString("updated", "")) else null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * 检查并更新题库。
     * @param force true = 手动点「立即更新」，忽略 24 小时节流和版本号；false = 开机自动检查
     */
    fun check(context: Context, force: Boolean = false): Result {
        val ctx = context.applicationContext
        if (!isConfigured()) {
            return Result(false, "未配置在线题库地址（用 GitHub Actions 打包会自动填好）")
        }
        val now = System.currentTimeMillis()
        if (!force && now - Prefs.bankLastCheckAt < 24 * 60 * 60_000L) {
            return Result(true, "今天已检查过，暂不重复下载")
        }
        Prefs.bankLastCheckAt = now

        // 主地址不通 / 下到坏文件，就自动换备用地址；都不行才报失败，旧题库原样保留
        val fetched = fetchValid()
            ?: return Result(false, "下载失败或题库格式有误（两个地址都试过），已保留当前题库")
        val (text, meta) = fetched

        val online = bankFile(ctx)
        if (!force && meta.version <= Prefs.bankVersion && online.exists()) {
            return Result(true, "已经是最新版 v${meta.version}", meta.version, meta.count)
        }

        // 先写临时文件，落盘后再校验一遍，最后才替换 —— 中途断电也不会留下半个坏文件
        val tmp = File(ctx.filesDir, "$BANK_FILE.tmp")
        return try {
            tmp.writeText(text, Charsets.UTF_8)
            if (validate(tmp.readText(Charsets.UTF_8)) == null) {
                tmp.delete()
                return Result(false, "题库写入校验没过，已保留当前题库")
            }
            if (online.exists()) online.delete()
            if (!tmp.renameTo(online)) {
                tmp.copyTo(online, overwrite = true)
                tmp.delete()
            }
            Prefs.bankVersion = meta.version
            Prefs.bankCount = meta.count
            Prefs.bankUpdatedAt = now
            QuestionBank.invalidate()   // 清缓存，下一道题立刻用新题库
            Result(true, "已更新到 v${meta.version}，共 ${meta.count} 题", meta.version, meta.count)
        } catch (e: Exception) {
            tmp.delete()
            Result(false, "保存失败，已保留当前题库")
        }
    }

    /** 恢复内置题库（删掉下载的在线文件） */
    fun restoreBuiltIn(context: Context): Result {
        val ctx = context.applicationContext
        return try {
            bankFile(ctx).delete()
            File(ctx.filesDir, "$BANK_FILE.tmp").delete()
            Prefs.bankVersion = 0
            Prefs.bankCount = 0
            Prefs.bankUpdatedAt = 0L
            Prefs.bankLastCheckAt = 0L
            QuestionBank.invalidate()
            Result(true, "已恢复内置题库")
        } catch (e: Exception) {
            Result(false, "恢复失败")
        }
    }

    /** 设置页展示用的一行摘要 */
    fun summary(context: Context): String {
        val ctx = context.applicationContext
        return if (bankFile(ctx).exists() && Prefs.bankVersion > 0) {
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(Prefs.bankUpdatedAt))
            "在线题库 v${Prefs.bankVersion} · ${Prefs.bankCount} 题 · 更新于 $time"
        } else {
            "内置题库 · 96 题（出厂自带）"
        }
    }

    /** 开机自动检查：后台线程跑，失败也静默，绝不阻塞计时服务 */
    fun autoCheckAsync(context: Context) {
        if (!Prefs.bankAuto || !isConfigured()) return
        val now = System.currentTimeMillis()
        if (now - Prefs.bankLastCheckAt < 24 * 60 * 60_000L) return
        val ctx = context.applicationContext
        Thread({
            try { check(ctx) } catch (e: Exception) { /* 静默 */ }
        }, "bank-update").start()
    }

    /** 依次尝试所有地址，返回第一份「下载成功且校验通过」的题库 */
    private fun fetchValid(): Pair<String, Meta>? {
        for (u in candidates()) {
            val text = download(u) ?: continue
            val meta = validate(text) ?: continue
            return text to meta
        }
        return null
    }

    /** 下载文本，带超时 / 大小限制 / 缓存穿透 */
    private fun download(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val sep = if (urlStr.contains("?")) "&" else "?"
            val target = URL(urlStr + sep + "t=" + System.currentTimeMillis())
            val c = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            conn = c
            if (c.responseCode !in 200..299) return null
            val bytes = c.inputStream.use { it.readBytes() }
            // 2MB 上限：防止误下到一个 HTML 错误页或者超大文件把盒子撑爆
            if (bytes.isEmpty() || bytes.size > 2 * 1024 * 1024) return null
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
