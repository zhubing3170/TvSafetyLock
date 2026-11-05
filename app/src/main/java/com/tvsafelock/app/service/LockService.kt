package com.tvsafelock.app.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tvsafelock.app.MainActivity
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.data.QuestionUpdate
import com.tvsafelock.app.license.License

/**
 * 计时服务（前台服务）。
 *
 * 为什么用前台服务：普通后台服务在电视盒子上几分钟就被系统回收了，
 * 挂一个常驻通知能让系统知道「这个 App 还在干活」，存活率高得多。
 *
 * 实现上用 15 秒轮询而不是精确闹钟：电视一直插电，不用担心耗电；
 * 而精确闹钟在国产定制系统上经常被吃掉。
 *
 * v1.1 起，是否该弹题完全由 LockClock（累计观看时长）决定，
 * 本类只负责「按时跳一下心跳」+「把答题页拉起来」+「开机补弹」+「题库自动检查」。
 */
class LockService : Service() {

    companion object {
        private const val CHANNEL_ID = "tv_lock_channel"
        private const val NOTIFY_ID = 1001
        @Volatile
        private var instance: LockService? = null

        fun isRunning(): Boolean = instance != null

        fun start(ctx: Context) {
            val intent = Intent(ctx, LockService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (e: Exception) {
                // 部分系统在后台启动服务会抛异常，忽略即可
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, LockService::class.java))
            } catch (e: Exception) {
                // ignore
            }
        }

        /** 兼容旧调用：答题结束后开启新一轮倒计时 */
        fun rescheduleNext(ctx: Context, extraMinutes: Int = 0) {
            LockClock.reset(ctx, extraMinutes)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastNotifyText = ""

    private val ticker = object : Runnable {
        override fun run() {
            handler.postDelayed(this, LockClock.TICK_MS)

            // 心跳统一由 LockClock 处理，内部会判断管控开关 / 家长暂停 / 正在答题
            val due = LockClock.tick(this@LockService)
            if (due) {
                QuizLauncher.show(this@LockService)
            } else if (Prefs.quizShowing) {
                // 兜底：孩子把答题页切到后台「装死」时，超过 2 分钟就强行拉回前台。
                // 无障碍开着的时候不用等到这一步，切走立刻就被拉回来了。
                val shownFor = System.currentTimeMillis() - Prefs.quizStartAt
                if (shownFor > 2 * 60_000L && !isSelfForeground()) {
                    QuizLauncher.show(this@LockService, force = true)
                }
            }
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        License.tick(this)
        LockClock.ensureStarted(this)

        // 每天最多自动检查一次题库更新；纯后台线程，失败静默，不阻塞计时
        QuestionUpdate.autoCheckAsync(this)

        createChannel()
        startForeground(NOTIFY_ID, buildNotification())
        handler.postDelayed(ticker, 3_000)

        // 开机补弹：开机瞬间系统桌面还没稳，直接 startActivity 容易被拒，
        // 等十几秒再由前台服务拉起来，成功率高得多。
        handler.postDelayed({
            if (Prefs.bootPendingQuiz) {
                Prefs.bootPendingQuiz = false
                QuizLauncher.show(this@LockService, force = true)
            }
        }, 12_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 判断本 App 是不是在前台（用来区分「孩子在答题」和「孩子已经切走了」） */
    private fun isSelfForeground(): Boolean {
        return try {
            val am = getSystemService(ActivityManager::class.java)
            am.runningAppProcesses?.any {
                it.processName == packageName &&
                        it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    // ==================== 通知 ====================

    private fun statusText(): String = when {
        !Prefs.enabled -> "管控已关闭"
        Prefs.isPaused() -> "家长已临时暂停管控"
        License.isTrialExpired(this) -> "试用已到期，请家长激活"
        Prefs.quizShowing -> "正在答题"
        else -> "下次答题还有 ${LockClock.remainingMinutes()} 分钟"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "管控运行状态",
                    NotificationManager.IMPORTANCE_MIN   // 最低优先级，不打扰看电视
                ).apply {
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        lastNotifyText = statusText()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("电视安全锁运行中")
            .setContentText(lastNotifyText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        val text = statusText()
        if (text == lastNotifyText) return
        lastNotifyText = text
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFY_ID, buildNotification())
        } catch (e: Exception) {
            // 通知权限被拒时会抛异常，忽略
        }
    }
}
