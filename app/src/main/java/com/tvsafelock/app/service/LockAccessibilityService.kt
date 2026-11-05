package com.tvsafelock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.tvsafelock.app.data.Prefs

/**
 * 无障碍服务 —— 「硬」拦截层。
 *
 * 两件事：
 *  1. 答题过程中吃掉遥控器的 主页键 / 返回键 / 菜单键 —— 孩子按键跳不出去
 *  2. 万一切到别的 App（有些电视桌面会把按键吞掉），检测到前台不是自己就把答题页拉回来
 *
 * 注意：这个权限必须家长手动去系统设置里打开，App 无法自动开启（系统规定）。
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Prefs.init(this)
        Prefs.a11yOn = true
        // 只初始化心跳，不动累计观看时长。旧版在这里写 nextFireAt 会跟新计时模型打架。
        LockClock.ensureStarted(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Prefs.a11yOn = false
        return super.onUnbind(intent)
    }

    /** 按键拦截 */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.quizShowing) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS -> true   // 返回 true = 这个键被我们吃掉了
            else -> false
        }
    }

    /** 窗口切换监听：答题中被切走就拉回来 */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Prefs.quizShowing) return
        if (!Prefs.enabled || Prefs.isPaused()) return

        val pkg = event.packageName?.toString() ?: return
        // 自己的界面不算「逃跑」
        if (pkg == packageName) return
        // 系统的输入法、音量面板等不算
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return

        QuizLauncher.show(this, force = true)
    }

    override fun onInterrupt() {
        // 服务被系统中断，无需处理
    }
}
