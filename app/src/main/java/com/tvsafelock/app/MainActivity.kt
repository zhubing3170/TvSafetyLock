package com.tvsafelock.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.license.License
import com.tvsafelock.app.service.LockClock
import com.tvsafelock.app.service.LockService
import com.tvsafelock.app.ui.ActivationScreen
import com.tvsafelock.app.ui.PinGateScreen
import com.tvsafelock.app.ui.SettingsScreen
import com.tvsafelock.app.ui.SetupScreen
import com.tvsafelock.app.ui.TvLockTheme

/**
 * 主界面（家长用的）：首次是引导页，之后进来先要密码
 */
class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        License.tick(this)

        // 安卓 13+ 通知要单独授权，前台服务才能正常显示状态
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 已完成引导的话，进来就把服务拉起来。
        // 只初始化心跳，绝不重置累计观看时长 —— 反复打开设置页不能刷时间。
        if (Prefs.setupDone && Prefs.enabled) {
            LockClock.ensureStarted(this)
            LockService.start(this)
        }

        setContent {
            TvLockTheme {
                var screen by remember {
                    mutableStateOf(if (Prefs.setupDone) "gate" else "setup")
                }

                when (screen) {
                    "setup" -> SetupScreen(
                        onFinish = {
                            // 引导完成：开一个全新的间隔，直接开工
                            LockClock.reset(this@MainActivity)
                            Prefs.enabled = true
                            LockService.start(this@MainActivity)
                            screen = "settings"
                        }
                    )

                    "gate" -> PinGateScreen(
                        onSuccess = { screen = "settings" }
                    )

                    "activation" -> ActivationScreen(
                        onBack = { screen = "settings" }
                    )

                    else -> SettingsScreen(
                        onOpenActivation = { screen = "activation" },
                        onLock = { screen = "gate" }
                    )
                }
            }
        }
    }
}
