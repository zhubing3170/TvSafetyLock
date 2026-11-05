package com.tvsafelock.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 各项系统权限的检查与跳转。
 * 这些权限 App 都没法自己开，只能引导家长去系统设置点一下。
 */
object Perms {

    /** 悬浮窗（能盖住别的 App） */
    fun canOverlay(ctx: Context): Boolean =
        Settings.canDrawOverlays(ctx)

    fun openOverlay(ctx: Context) {
        safeStart(
            ctx,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }

    /** 无障碍（拦主页键） */
    fun isA11yOn(ctx: Context): Boolean {
        val enabled = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) {
            null
        } ?: return false
        return enabled.contains(ctx.packageName) &&
                enabled.contains("LockAccessibilityService", ignoreCase = true)
    }

    fun openA11y(ctx: Context) {
        safeStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** 忽略电池优化（防止后台被冻结） */
    fun isIgnoringBattery(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun openBattery(ctx: Context) {
        // 先试「直接申请」，不行再退到设置列表
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${ctx.packageName}"))
        if (!safeStart(ctx, direct)) {
            safeStart(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** 通知权限（安卓 13+ 需要运行时授权） */
    fun notificationsOn(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun openNotificationSettings(ctx: Context) {
        safeStart(
            ctx,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        )
    }

    /** 打开本 App 的系统详情页（国产电视的「自启动管理」入口经常藏在这里） */
    fun openAppDetail(ctx: Context) {
        safeStart(
            ctx,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        )
    }

    private fun safeStart(ctx: Context, intent: Intent): Boolean {
        return try {
            if (ctx !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
