package com.tvsafelock.app.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * 所有本地配置的读写入口。
 * 之所以做成 object（单例）：服务、无障碍、界面三处都要读配置，统一入口最省事。
 */
object Prefs {

    private const val FILE = "tv_lock_prefs"
    private lateinit var sp: SharedPreferences

    // 科目常量
    const val SUBJECT_CHINESE = "chinese"
    const val SUBJECT_MATH = "math"
    const val SUBJECT_ENGLISH = "english"

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!sp.getBoolean("first_run_done", false)) {
            val now = System.currentTimeMillis()
            sp.edit()
                .putBoolean("first_run_done", true)
                .putLong("installed_at", now)
                .putLong("last_seen", now)
                .apply()
        }
    }

    // ======================= 年级 =======================
    var grade: Int
        get() = sp.getInt("grade", 3)
        set(value) = sp.edit().putInt("grade", value.coerceIn(1, 6)).apply()

    // ======================= 科目（可多选） =======================
    var subjects: Set<String>
        get() = sp.getStringSet("subjects", setOf(SUBJECT_CHINESE, SUBJECT_MATH)) ?: setOf(SUBJECT_MATH)
        set(value) {
            // 至少保留一科，否则没题可出
            val safe = if (value.isEmpty()) setOf(SUBJECT_MATH) else value
            sp.edit().putStringSet("subjects", safe).apply()
        }

    // ======================= 弹题间隔（分钟） =======================
    var intervalMin: Int
        get() = sp.getInt("interval_min", 10)
        set(value) = sp.edit().putInt("interval_min", value.coerceIn(1, 120)).apply()

    // ======================= 家长密码（存哈希，不存明文） =======================
    /** 空字符串表示还没设置密码 */
    var pinHash: String
        get() = sp.getString("pin_hash", "") ?: ""
        set(value) = sp.edit().putString("pin_hash", value).apply()

    fun hasPin(): Boolean = pinHash.isNotEmpty()

    fun setPin(pin: String) {
        pinHash = sha256("tv-safety-lock::v1::$pin")
    }

    fun checkPin(pin: String): Boolean = pinHash.isNotEmpty() && pinHash == sha256("tv-safety-lock::v1::$pin")

    // ======================= 管控总开关 / 临时暂停 =======================
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    /** 家长临时暂停管控的截止时间戳（毫秒） */
    var pausedUntil: Long
        get() = sp.getLong("paused_until", 0L)
        set(value) = sp.edit().putLong("paused_until", value).apply()

    fun isPaused(): Boolean = pausedUntil > System.currentTimeMillis()

    // ======================= 引导流程是否已完成 =======================
    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(value) = sp.edit().putBoolean("setup_done", value).apply()

    // ======================= 下一次该弹题的时间（仅作展示/兜底镜像） =======================
    /**
     * 注意：从 v1.1 起，真正的倒计时判定不再依赖这个墙钟时间，
     * 而是用 watchMs（开机运行时长累计）。这个字段只用于通知栏显示和异常兜底，
     * 避免孩子改系统时间或者反复开关机来绕过管控。
     */
    var nextFireAt: Long
        get() = sp.getLong("next_fire_at", 0L)
        set(value) = sp.edit().putLong("next_fire_at", value).apply()

    /** 当前是否正在答题（防止重复弹出）。这是持久化的，断电重启后能知道「上次有题没答完」。 */
    var quizShowing: Boolean
        get() = sp.getBoolean("quiz_showing", false)
        set(value) = sp.edit().putBoolean("quiz_showing", value).apply()

    /** 本次答题什么时候弹出来的（用于判断孩子是不是把界面切走了） */
    var quizStartAt: Long
        get() = sp.getLong("quiz_start_at", 0L)
        set(value) = sp.edit().putLong("quiz_start_at", value).apply()

    // ======================= 抗重启的计时状态（v1.1 新增） =======================
    /** 自上次答完题以来，累计的「有效观看时长」（毫秒）。关机重启不会清零。 */
    var watchMs: Long
        get() = sp.getLong("watch_ms", 0L)
        set(value) = sp.edit().putLong("watch_ms", value.coerceAtLeast(0L)).apply()

    /** 额外要等的时间（答错惩罚 / 家长跳过 / 后台弹出失败重试），叠加在间隔之上 */
    var bonusMs: Long
        get() = sp.getLong("bonus_ms", 0L)
        set(value) = sp.edit().putLong("bonus_ms", value.coerceAtLeast(0L)).apply()

    /** 上一次计时心跳对应的 SystemClock.elapsedRealtime()（开机后单调时钟，重启会归零） */
    var lastElapsed: Long
        get() = sp.getLong("last_elapsed", 0L)
        set(value) = sp.edit().putLong("last_elapsed", value).apply()

    /** 上一次计时心跳的墙钟时间，用来推算「电视关了多久」 */
    var lastHeartbeat: Long
        get() = sp.getLong("last_heartbeat", 0L)
        set(value) = sp.edit().putLong("last_heartbeat", value).apply()

    /** 短时间内（3 分钟内）连续开关机的次数，用来识别「靠重启刷时间」 */
    var quickReboots: Int
        get() = sp.getInt("quick_reboots", 0)
        set(value) = sp.edit().putInt("quick_reboots", value.coerceIn(0, 99)).apply()

    var lastBootAt: Long
        get() = sp.getLong("last_boot_at", 0L)
        set(value) = sp.edit().putLong("last_boot_at", value).apply()

    /** 开机后要不要补弹一道题（服务起来后延迟执行，避开系统启动那一瞬） */
    var bootPendingQuiz: Boolean
        get() = sp.getBoolean("boot_pending_quiz", false)
        set(value) = sp.edit().putBoolean("boot_pending_quiz", value).apply()

    // ======================= 在线题库（v1.1 新增） =======================
    var bankVersion: Int
        get() = sp.getInt("bank_version", 0)
        set(value) = sp.edit().putInt("bank_version", value).apply()

    var bankCount: Int
        get() = sp.getInt("bank_count", 0)
        set(value) = sp.edit().putInt("bank_count", value).apply()

    var bankUpdatedAt: Long
        get() = sp.getLong("bank_updated_at", 0L)
        set(value) = sp.edit().putLong("bank_updated_at", value).apply()

    var bankLastCheckAt: Long
        get() = sp.getLong("bank_last_check_at", 0L)
        set(value) = sp.edit().putLong("bank_last_check_at", value).apply()

    /** 开机后自动检查一次题库更新（默认开） */
    var bankAuto: Boolean
        get() = sp.getBoolean("bank_auto", true)
        set(value) = sp.edit().putBoolean("bank_auto", value).apply()

    // ======================= 试用统计 =======================
    var installedAt: Long
        get() = sp.getLong("installed_at", System.currentTimeMillis())
        set(value) = sp.edit().putLong("installed_at", value).apply()

    /** 累计已使用时长（毫秒）；把系统时间往回调也没法减少它 */
    var usedMs: Long
        get() = sp.getLong("used_ms", 0L)
        set(value) = sp.edit().putLong("used_ms", value).apply()

    var lastSeen: Long
        get() = sp.getLong("last_seen", 0L)
        set(value) = sp.edit().putLong("last_seen", value).apply()

    // ======================= 授权 =======================
    var licensed: Boolean
        get() = sp.getBoolean("licensed", false)
        set(value) = sp.edit().putBoolean("licensed", value).apply()

    // ======================= 无障碍是否被系统启用（缓存一下） =======================
    var a11yOn: Boolean
        get() = sp.getBoolean("a11y_on", false)
        set(value) = sp.edit().putBoolean("a11y_on", value).apply()

    // ======================= 工具方法 =======================
    fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
