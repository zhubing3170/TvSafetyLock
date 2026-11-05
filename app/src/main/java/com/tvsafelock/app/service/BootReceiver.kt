package com.tvsafelock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvsafelock.app.data.Prefs

/**
 * 开机自启 + 关机收尾。
 *
 * v1.1 的重点：**开机绝不再重置倒计时**。
 * 旧版在这里写 `nextFireAt = 现在 + 完整间隔`，还把 quizShowing 清成 false，
 * 孩子只要重启电视 / 题目弹出来时直接断电，就能无限绕过。
 * 现在把恢复逻辑交给 LockClock.onBoot()：累计时长原样保留，
 * 有没答完的题就补弹，反复快速开关机还会被判定为刷时间。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // 关机 / 断电前把心跳时间记准，用于开机时推算「关了多久」。
        // 就算没有收到这个广播（直接拔电源），最后一次 15 秒心跳也足够用了。
        if (action == Intent.ACTION_SHUTDOWN || action == "android.intent.action.QUICKBOOT_POWEROFF") {
            Prefs.init(context)
            Prefs.lastHeartbeat = System.currentTimeMillis()
            return
        }

        // 不处理 LOCKED_BOOT_COMPLETED：direct boot 阶段凭据加密存储还没解锁，
        // 读写 SharedPreferences 会直接抛异常。解锁后的 BOOT_COMPLETED 足够用了。
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        Prefs.init(context)
        if (!Prefs.enabled) return
        if (!Prefs.setupDone) return          // 还没走完引导，先不启动

        // onBoot 内部对同一次开机的多个广播做了去重
        val needQuiz = LockClock.onBoot(context)
        Prefs.bootPendingQuiz = Prefs.bootPendingQuiz || needQuiz

        // 服务起来后，待补弹标记会延迟 12 秒被消费（避开系统启动那一瞬）
        LockService.start(context)
    }
}
