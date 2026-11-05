package com.tvsafelock.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.data.Question
import com.tvsafelock.app.data.QuestionBank
import com.tvsafelock.app.license.License
import com.tvsafelock.app.service.LockClock
import com.tvsafelock.app.service.LockService
import com.tvsafelock.app.ui.ActivationScreen
import com.tvsafelock.app.ui.PinGateScreen
import com.tvsafelock.app.ui.QuizScreen
import com.tvsafelock.app.ui.TvLockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 答题页：全屏盖在最上层。
 *
 * 交互规则：
 *  - 答对 → 1.8 秒后自动关闭，重新开始倒计时
 *  - 答错 → 显示正确答案 2.2 秒后换一道新题；连续错 3 次，本次锁定 2 分钟
 *  - 右上角「家长」→ 输密码可临时跳过
 *  - 试用到期 → 这一页变成激活页
 */
class QuizActivity : ComponentActivity() {

    private val questionState = mutableStateOf<Question?>(null)
    private val phaseState = mutableStateOf(0)      // 0 答题中 / 1 答对 / 2 答错
    private val chosenState = mutableStateOf(-1)
    private val wrongState = mutableStateOf(0)
    private val parentState = mutableStateOf(false)
    private val expiredState = mutableStateOf(false)

    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        License.tick(this)
        Prefs.quizShowing = true

        // 电视上答题时不让屏幕休眠
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        hideSystemBars()

        // 答题期间禁止返回键（无障碍没开时这是最后一道防线）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 什么都不做，就是不让你退
            }
        })

        expiredState.value = License.isTrialExpired(this)
        if (!expiredState.value) {
            loadQuestion()
        }

        setContent {
            TvLockTheme {
                when {
                    expiredState.value -> ActivationScreen(onBack = { finishQuiz() })

                    parentState.value -> PinGateScreen(
                        onSuccess = {
                            parentState.value = false
                            // 家长跳过：把下次时间延长一个完整间隔，等于这次免答
                            finishQuiz(extraMinutes = Prefs.intervalMin)
                        },
                        onCancel = { parentState.value = false }
                    )

                    questionState.value != null -> QuizScreen(
                        question = questionState.value!!,
                        phase = phaseState.value,
                        chosen = chosenState.value,
                        wrongCount = wrongState.value,
                        onAnswer = { index -> handleAnswer(index) },
                        onParent = { parentState.value = true }
                    )

                    else -> { /* 理论上不会到这里 */ }
                }
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun loadQuestion() {
        questionState.value = QuestionBank.pickAny(this, Prefs.grade, Prefs.subjects)
        phaseState.value = 0
        chosenState.value = -1
    }

    private fun handleAnswer(index: Int) {
        val q = questionState.value ?: return
        if (phaseState.value != 0 || closing) return

        chosenState.value = index

        if (index == q.answer) {
            // 答对了
            phaseState.value = 1
            lifecycleScope.launch {
                delay(1800)
                finishQuiz()
            }
        } else {
            // 答错了
            phaseState.value = 2
            val wrong = wrongState.value + 1
            wrongState.value = wrong
            lifecycleScope.launch {
                delay(2200)
                if (wrong >= 3) {
                    // 连错 3 次：这次先锁 2 分钟，别把孩子逼急了
                    finishQuiz(extraMinutes = 2)
                } else {
                    loadQuestion()
                }
            }
        }
    }

    /** 关闭答题页并重设下次弹题时间 */
    private fun finishQuiz(extraMinutes: Int = 0) {
        closing = true
        if (expiredState.value) {
            // 试用到期：每小时提醒一次去激活就够了，别把家长烦死
            LockClock.snooze(this, 60)
        } else {
            // 答对 / 家长跳过 / 连错 3 次：开启新一轮完整间隔（含额外惩罚时长）
            LockClock.reset(this, extraMinutes)
        }
        // 保证计时服务还活着
        LockService.start(this)
        finish()
    }

    override fun onDestroy() {
        // 这里**故意不清 quizShowing**：只要这道题没答完（被系统杀掉、断电、直接拔电源），
        // 前台服务会把它拉回来；真断电了，开机广播也会立刻补弹这道题。
        // 旧版在这里调用 rescheduleNext 重新计时，等于白送孩子一个完整间隔。
        super.onDestroy()
    }
}
