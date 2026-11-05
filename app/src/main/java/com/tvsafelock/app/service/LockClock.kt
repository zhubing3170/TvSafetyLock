package com.tvsafelock.app.service

import android.content.Context
import android.os.SystemClock
import com.tvsafelock.app.data.Prefs

/**
 * 抗重启的计时核心（v1.1 重写）。
 *
 * ── 为什么不再用「下一次弹题的绝对时间」 ──────────────────────────
 * 旧版把 nextFireAt（墙钟时间戳）存起来，开机广播里又把它重置成「现在 + 一个完整间隔」。
 * 结果就是：孩子只要重启一次电视，就白赚一整个间隔；题目弹出来时直接断电，
 * 开机后连「有题没答完」的标记都被清掉，等于无限续命。
 *
 * ── 现在的模型 ───────────────────────────────────────────────
 * 只累计「真正开机看着电视的时间」watchMs，用 SystemClock.elapsedRealtime()
 * （开机后单调递增、不受改系统时间影响）。这个值是持久化的：
 *   · 关机 → 停止累计（孩子没在看，不该算时间，也不用补扣）
 *   · 开机 → 从原来的剩余时长接着走，**绝不重置**
 *   · 改系统时间 → 完全不影响，因为判定根本不看墙钟
 *   · 答题 / 家长暂停期间 → 不累计，但心跳继续走
 *
 * 另外三道防绕过的锁：
 *   ① quizShowing 是持久化的，题目没答完就断电 → 开机立刻补弹这道题
 *   ② 3 分钟内反复开关机累计 3 次 → 判定为刷时间，直接弹题
 *   ③ 家长把间隔改小 → 不重置累计，已超时就立刻弹题（堵住「先调大再调小」）
 */
object LockClock {

    /** 与 LockService 的轮询间隔一致 */
    const val TICK_MS = 15_000L

    private const val TICK_MAX_MS = 5 * 60_000L          // 单次心跳最多补 5 分钟，防止服务被杀后一次性补太多
    private const val QUICK_REBOOT_WINDOW = 3 * 60_000L  // 关机不到 3 分钟又开 → 可疑
    private const val OVERNIGHT_GAP = 2 * 60 * 60_000L   // 关机超过 2 小时 → 视为正常隔夜
    private const val BOOT_GRACE_MS = 2 * 60_000L        // 隔夜开机最多给 2 分钟缓冲
    private const val BOOT_DEDUP_MS = 60_000L            // 同一次开机会收到好几个广播，60 秒内只处理一次

    private fun intervalMs(): Long = Prefs.intervalMin * 60_000L

    /** 距离下次弹题还剩多少毫秒 */
    fun remainingMs(): Long =
        (intervalMs() + Prefs.bonusMs - Prefs.watchMs).coerceAtLeast(0L)

    /** 通知栏显示用，向上取整，避免出现「还有 0 分钟」 */
    fun remainingMinutes(): Long = (remainingMs() + 59_999L) / 60_000L

    /** 只用于通知栏展示和异常兜底，不参与是否弹题的判定 */
    fun nextFireAt(): Long = System.currentTimeMillis() + remainingMs()

    /** 首次启动时初始化心跳，绝不覆盖已有的 watchMs */
    fun ensureStarted(context: Context) {
        Prefs.init(context)
        if (Prefs.lastHeartbeat <= 0L) {
            Prefs.lastElapsed = SystemClock.elapsedRealtime()
            Prefs.lastHeartbeat = System.currentTimeMillis()
            Prefs.nextFireAt = nextFireAt()
        }
    }

    /**
     * 每 15 秒心跳一次。
     * @return true 表示这一刻该弹题了（服务负责把答题页拉起来）
     */
    fun tick(context: Context): Boolean {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        // elapsedRealtime 在重启后归零，旧的 lastElapsed 会远大于 nowElapsed；
        // 这时按一个标准心跳算，关机那段时间不会被计进来看电视的时长。
        val raw = nowElapsed - Prefs.lastElapsed
        val delta = if (raw > 0L && raw <= TICK_MAX_MS) raw else TICK_MS
        Prefs.lastElapsed = nowElapsed
        Prefs.lastHeartbeat = nowWall
        Prefs.nextFireAt = nextFireAt()

        // 管控关闭、家长暂停、正在答题：都不算有效观看时长
        if (!Prefs.enabled || Prefs.isPaused() || Prefs.quizShowing) return false

        Prefs.watchMs = Prefs.watchMs + delta
        Prefs.nextFireAt = nextFireAt()
        return Prefs.watchMs >= intervalMs() + Prefs.bonusMs
    }

    /** 答完题 / 家长跳过 / 手动开启管控：开启一个全新的间隔 */
    fun reset(context: Context, extraMinutes: Int = 0) {
        Prefs.quizShowing = false
        Prefs.watchMs = 0L
        Prefs.bonusMs = extraMinutes.coerceAtLeast(0) * 60_000L
        Prefs.lastElapsed = SystemClock.elapsedRealtime()
        Prefs.lastHeartbeat = System.currentTimeMillis()
        Prefs.quickReboots = 0                 // 已经「付过费」，清空可疑重启计数
        Prefs.bootPendingQuiz = false
        Prefs.nextFireAt = nextFireAt()
    }

    /** 后台弹窗失败、试用到期提醒等场景：顺延 N 分钟再来 */
    fun snooze(context: Context, minutes: Int) = reset(context, minutes)

    /**
     * 家长改间隔：只改档位，不动累计时长。
     * 这样把 20 分钟改成 5 分钟时，如果已经看了 8 分钟，下一次心跳就会立刻弹题，
     * 不会出现「改一下设置就白赚一个完整间隔」。
     */
    fun onIntervalChanged(context: Context, minutes: Int) {
        Prefs.intervalMin = minutes
        Prefs.lastElapsed = SystemClock.elapsedRealtime()
        Prefs.nextFireAt = nextFireAt()
    }

    /**
     * 开机恢复。
     * @return true 表示开机后需要立刻补弹一道题（由服务延迟执行，避开系统启动那一瞬）
     */
    fun onBoot(context: Context): Boolean {
        Prefs.init(context)
        val now = System.currentTimeMillis()

        // 同一次开机会连着收到 BOOT_COMPLETED / LOCKED_BOOT_COMPLETED / QUICKBOOT_POWERON，
        // 只认第一个，否则「可疑重启」计数会被广播次数撑爆。
        if (Prefs.lastBootAt > 0L && now - Prefs.lastBootAt < BOOT_DEDUP_MS) return false

        val heartbeat = Prefs.lastHeartbeat
        val offMs = if (heartbeat in 1 until now) now - heartbeat else -1L

        Prefs.lastBootAt = now
        Prefs.lastElapsed = 0L              // 单调时钟已归零，下一心跳按标准 15 秒算
        Prefs.lastHeartbeat = now

        // ① 上次题目没答完就断电 / 拔电源 / 被杀 → quizShowing 还留着，开机必须补上
        if (Prefs.quizShowing) {
            Prefs.quizStartAt = now
            Prefs.nextFireAt = now
            return true
        }

        // ② 时间被回拨，或者 3 分钟内又开机 → 记一次可疑重启
        if (offMs < 0L || (offMs >= 0L && offMs <= QUICK_REBOOT_WINDOW)) {
            Prefs.quickReboots = Prefs.quickReboots + 1
            if (Prefs.quickReboots >= 3) {
                // 反复开关机刷时间：直接到期，开机就弹题
                Prefs.watchMs = intervalMs() + Prefs.bonusMs
                Prefs.nextFireAt = now
                return true
            }
        } else {
            Prefs.quickReboots = 0
        }

        // ③ 正常隔夜关机：剩余时长照旧保留，只是给最多 2 分钟开机缓冲，
        //    免得一打开电视就被题目糊脸。注意绝不会重置成完整间隔。
        if (offMs > OVERNIGHT_GAP && remainingMs() < BOOT_GRACE_MS) {
            Prefs.watchMs = (intervalMs() + Prefs.bonusMs - BOOT_GRACE_MS).coerceAtLeast(0L)
        }

        Prefs.nextFireAt = nextFireAt()
        // 关机前已经累计到点但没来得及弹（例如服务被杀），开机后第一次心跳会立刻弹，
        // 这里不用额外处理，watchMs 原样保留即可。
        return false
    }
}
