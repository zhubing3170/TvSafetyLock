package com.tvsafelock.app.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.tvsafelock.app.QuizActivity
import com.tvsafelock.app.data.Prefs

/**
 * 负责把答题页拉起来。
 * 因为本 App 申请了「悬浮窗」权限，所以在安卓 10+ 的后台启动限制下也能弹出界面。
 */
object QuizLauncher {

    private var lastForceAt = 0L

    fun show(ctx: Context, force: Boolean = false) {
        if (Prefs.quizShowing && !force) return
        // 强拉的时候做个节流，避免疯狂弹窗
        if (force) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastForceAt < 2500) return
            lastForceAt = now
        }

        Prefs.quizShowing = true
        Prefs.quizStartAt = System.currentTimeMillis()
        val intent = Intent(ctx, QuizActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // 后台启动被系统拦了：顺延 1 分钟再试。
            // 注意不能简单写 nextFireAt —— 判定走的是累计观看时长，得由 LockClock 统一处理。
            LockClock.snooze(ctx, 1)
        }
    }
}
