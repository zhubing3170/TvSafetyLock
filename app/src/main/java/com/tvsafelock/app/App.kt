package com.tvsafelock.app

import android.app.Application
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.license.License

/**
 * 全局入口：只做两件事 —— 初始化配置存储、记录一次使用时长（用于 3 天试用判定）
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        License.tick(this)
    }
}
