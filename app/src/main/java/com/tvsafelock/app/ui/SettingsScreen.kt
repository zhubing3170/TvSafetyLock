package com.tvsafelock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.license.License
import com.tvsafelock.app.service.LockClock
import com.tvsafelock.app.service.LockService
import com.tvsafelock.app.service.QuizLauncher
import kotlinx.coroutines.delay

/**
 * 家长设置主界面
 */
@Composable
fun SettingsScreen(onOpenActivation: () -> Unit, onLock: () -> Unit) {

    val ctx = LocalContext.current

    // 本地状态镜像：点一下立刻能看到界面变化
    var grade by remember { mutableStateOf(Prefs.grade) }
    var subjects by remember { mutableStateOf(Prefs.subjects) }
    var interval by remember { mutableStateOf(Prefs.intervalMin) }
    var enabled by remember { mutableStateOf(Prefs.enabled) }
    var showChangePin by remember { mutableStateOf(false) }

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val paused = remember(tick) { Prefs.isPaused() }
    val serviceOn = remember(tick) { LockService.isRunning() }
    val licensed = remember(tick) { License.isLicensed() }
    val daysLeft = remember(tick) { License.trialDaysLeft(ctx) }
    val expired = remember(tick) { License.isTrialExpired(ctx) }

    if (showChangePin) {
        ChangePinScreen(onDone = { showChangePin = false })
    } else {
        SettingsContent(
            grade = grade,
            subjects = subjects,
            interval = interval,
            enabled = enabled,
            paused = paused,
            serviceOn = serviceOn,
            licensed = licensed,
            daysLeft = daysLeft,
            expired = expired,
            onGrade = { grade = it; Prefs.grade = it },
            onSubjects = { subjects = it; Prefs.subjects = it },
            onInterval = {
                interval = it
                // 只改档位、不清零累计：把 20 分钟改 5 分钟时若已看超时，下次心跳就弹题
                LockClock.onIntervalChanged(ctx, it)
            },
            onToggleEnabled = { v ->
                enabled = v
                Prefs.enabled = v
                if (v) {
                    LockClock.reset(ctx)
                    LockService.start(ctx)
                } else {
                    LockService.stop(ctx)
                }
            },
            onTogglePause = {
                Prefs.pausedUntil = if (paused) 0L else System.currentTimeMillis() + 30 * 60_000L
            },
            onTest = { QuizLauncher.show(ctx, force = true) },
            onChangePin = { showChangePin = true },
            onOpenActivation = onOpenActivation,
            onLock = onLock
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    grade: Int,
    subjects: Set<String>,
    interval: Int,
    enabled: Boolean,
    paused: Boolean,
    serviceOn: Boolean,
    licensed: Boolean,
    daysLeft: Int,
    expired: Boolean,
    onGrade: (Int) -> Unit,
    onSubjects: (Set<String>) -> Unit,
    onInterval: (Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onTest: () -> Unit,
    onChangePin: () -> Unit,
    onOpenActivation: () -> Unit,
    onLock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 44.dp, vertical = 24.dp)
    ) {

        // ============ 顶部状态 ============
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PageTitle("家长设置", "改完立即生效，不用重启电视")
            StatusBadge(
                text = when {
                    !enabled -> "已关闭"
                    paused -> "已暂停"
                    expired -> "试用到期"
                    serviceOn -> "运行中"
                    else -> "未运行"
                },
                color = when {
                    !enabled || paused -> InkGray
                    expired -> WarnRed
                    serviceOn -> OkGreen
                    else -> WarnRed
                }
            )
        }

        if (expired) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEE2E2), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3 天免费试用已结束，请激活后继续使用", color = WarnRed, fontSize = 13.sp)
                    FocusCard(onClick = onOpenActivation, modifier = Modifier.width(120.dp)) {
                        Text("去激活", color = BrandBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 年级 ============
        SectionLabel("孩子现在读几年级？")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (1..6).forEach { g ->
                val sel = grade == g
                FocusCard(
                    onClick = { onGrade(g) },
                    selected = sel,
                    modifier = Modifier.width(118.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$g",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) BrandBlue else InkDark
                        )
                        Text("年级", fontSize = 11.sp, color = InkGray)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 科目 ============
        SectionLabel("出哪些科目？（可多选，至少一科）")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                Prefs.SUBJECT_CHINESE to "语文",
                Prefs.SUBJECT_MATH to "数学",
                Prefs.SUBJECT_ENGLISH to "英语"
            ).forEach { (key, name) ->
                val sel = subjects.contains(key)
                FocusCard(
                    onClick = {
                        val next = if (sel) subjects - key else subjects + key
                        if (next.isNotEmpty()) onSubjects(next)
                    },
                    selected = sel,
                    modifier = Modifier.width(146.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(22.dp)
                                .height(22.dp)
                                .background(
                                    if (sel) BrandBlue else Color(0xFFE2E8F0),
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Text("✓", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(name, fontSize = 16.sp, color = if (sel) BrandBlue else InkDark)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 间隔 ============
        SectionLabel("隔多久弹一道题？")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(5, 10, 15, 20).forEach { m ->
                val sel = interval == m
                FocusCard(
                    onClick = { onInterval(m) },
                    selected = sel,
                    modifier = Modifier.width(126.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$m",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) BrandBlue else InkDark
                        )
                        Text("分钟", fontSize = 11.sp, color = InkGray)
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 管控状态 ============
        SectionLabel("管控状态")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FocusCard(
                onClick = { onToggleEnabled(!enabled) },
                selected = enabled,
                modifier = Modifier.width(180.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (enabled) "管控已开启" else "管控已关闭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) OkGreen else InkGray
                    )
                    Text(if (enabled) "点此关闭" else "点此开启", fontSize = 11.sp, color = InkGray)
                }
            }

            FocusCard(onClick = onTogglePause, modifier = Modifier.width(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (paused) "取消暂停" else "暂停 30 分钟",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkDark
                    )
                    Text("家长自己看时用", fontSize = 11.sp, color = InkGray)
                }
            }

            FocusCard(onClick = onTest, modifier = Modifier.width(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("弹一道试试", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                    Text("测试用", fontSize = 11.sp, color = InkGray)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 家长与授权 ============
        SectionLabel("家长与授权")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FocusCard(onClick = onChangePin, modifier = Modifier.width(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("修改密码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                    Text("4 位数字", fontSize = 11.sp, color = InkGray)
                }
            }

            FocusCard(onClick = onOpenActivation, modifier = Modifier.width(220.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (licensed) "已激活" else "激活 / 试用信息",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (licensed) OkGreen else BrandBlue
                    )
                    Text(
                        if (licensed) "永久有效" else "剩余 $daysLeft 天",
                        fontSize = 11.sp,
                        color = InkGray
                    )
                }
            }

            FocusCard(onClick = onLock, modifier = Modifier.width(180.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("锁定设置", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                    Text("回到密码验证", fontSize = 11.sp, color = InkGray)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // ============ 在线题库 ============
        BankSection()

        Spacer(Modifier.height(26.dp))
        Text(
            "小提示：答题页右上角有「家长」按钮，输入密码可以临时跳过本次答题。",
            fontSize = 11.sp,
            color = InkGray
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 改密码：先验旧密码，再输两遍新密码 */
@Composable
private fun ChangePinScreen(onDone: () -> Unit) {
    var stage by remember { mutableStateOf(0) }   // 0 旧密码 / 1 新密码 / 2 确认
    var input by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        PageTitle(
            when (stage) {
                0 -> "请输入当前密码"
                1 -> "设置新密码"
                else -> "再输一次新密码"
            },
            "改完请记牢，忘了只能卸载重装"
        )
        Spacer(Modifier.height(12.dp))
        if (error.isNotEmpty()) Text(error, color = WarnRed, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))

        PinPad(
            value = input,
            onValueChange = { v ->
                input = v
                if (v.length == 4) {
                    when (stage) {
                        0 -> {
                            if (Prefs.checkPin(v)) {
                                stage = 1; input = ""; error = ""
                            } else {
                                input = ""; error = "密码不对"
                            }
                        }
                        1 -> {
                            newPin = v; stage = 2; input = ""; error = ""
                        }
                        else -> {
                            if (v == newPin) {
                                Prefs.setPin(v)
                                onDone()
                            } else {
                                stage = 1; input = ""; newPin = ""; error = "两次不一致，重来"
                            }
                        }
                    }
                }
            },
            onConfirm = { }
        )
    }
}
