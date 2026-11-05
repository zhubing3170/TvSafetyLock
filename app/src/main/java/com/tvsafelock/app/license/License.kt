package com.tvsafelock.app.license

import android.content.Context
import android.provider.Settings
import com.tvsafelock.app.BuildConfig
import com.tvsafelock.app.data.Prefs
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 离线授权（纯数字激活码，遥控器输入不费劲）。
 *
 * 原理：
 *  1. 每台电视有自己的「设备码」：8 位纯数字，来自系统 Android ID 的哈希
 *  2. 激活码 = HMAC-SHA256(密钥 LICENSE_SECRET, 设备码) 取模 10^12，得到 12 位纯数字
 *  3. App 内置同一把密钥，用户输入时重算一遍对比即可
 *
 * 密钥不在源码里！源码公开到 GitHub 也不会泄露。
 * 密钥在打包时由 -PLICENSE_SECRET=xxx 注入，或读环境变量 LICENSE_SECRET；
 * GitHub Actions 用仓库 Secret 注入。都没配时才退回演示密钥（仅供自己测试）。
 * ⚠️ 正式发版前务必配好自己的密钥，见 README「发版前必改清单」。
 */
object License {

    /** 构建时注入；空串时用演示密钥兜底，保证工程 Clone 下来直接能编译 */
    private val SECRET: String = BuildConfig.LICENSE_SECRET.ifBlank { "DEMO-KEY-DO-NOT-SHIP" }

    /** 试用时长：3 天 */
    private const val TRIAL_MS = 3L * 24 * 60 * 60 * 1000

    fun isLicensed(): Boolean = Prefs.licensed

    /** 设备码：8 位数字，展示给家长发给你 */
    fun deviceCode(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest("dev::$androidId".toByteArray())
        return toDigits(digest, 8)
    }

    /** 根据设备码算出应该输入的 12 位激活码 */
    fun expectedCode(context: Context): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))
        val raw = mac.doFinal(deviceCode(context).toByteArray())
        return toDigits(raw, 12)
    }

    /** 家长输入的码对不对 */
    fun activate(context: Context, input: String): Boolean {
        val clean = input.filter { it.isDigit() }
        if (clean.length != 12) return false
        if (clean == expectedCode(context)) {
            Prefs.licensed = true
            return true
        }
        return false
    }

    /** 试用剩余毫秒（已激活则视为无限） */
    fun trialRemainMs(context: Context): Long {
        if (isLicensed()) return Long.MAX_VALUE / 2
        val byWall = System.currentTimeMillis() - Prefs.installedAt
        // 取二者最大值：孩子把系统时间往回调，usedMs 也不会减少
        val elapsed = maxOf(byWall, Prefs.usedMs)
        return (TRIAL_MS - elapsed).coerceAtLeast(0L)
    }

    /** 试用剩余天数（向上取整，0 表示已到期） */
    fun trialDaysLeft(context: Context): Int {
        if (isLicensed()) return 999
        val ms = trialRemainMs(context)
        if (ms <= 0) return 0
        return Math.ceil(ms.toDouble() / (24 * 60 * 60 * 1000.0)).toInt()
    }

    fun isTrialExpired(context: Context): Boolean = !isLicensed() && trialRemainMs(context) <= 0

    /**
     * 累计使用时长。
     * 每次 App / 服务启动调用一次，把「上次记录时间 → 现在」的差值累加进去。
     * 时间被往回调时差值为负，直接丢弃 —— 保证用时只增不减。
     */
    fun tick(context: Context) {
        val now = System.currentTimeMillis()
        val last = Prefs.lastSeen
        if (last in 1 until now) {
            Prefs.usedMs = Prefs.usedMs + (now - last)
        }
        Prefs.lastSeen = now
    }

    /** 把哈希值转成固定长度数字串 */
    private fun toDigits(bytes: ByteArray, digits: Int): String {
        val mod = BigInteger.TEN.pow(digits)
        val v = BigInteger(1, bytes).mod(mod)
        return v.toString().padStart(digits, '0')
    }

    /** 界面展示用：4 位一组，好抄 */
    fun pretty(code: String): String {
        return code.chunked(4).joinToString(" ")
    }
}
