package com.tvsafelock.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay

/**
 * 首次启动的引导页：开权限 → 设密码
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(onFinish: () -> Unit) {

    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    // 每秒刷新一次权限状态（家长去设置里开完回来，界面要能自动变绿）
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            tick++
        }
    }

    val overlay = remember(tick) { Perms.canOverlay(ctx) }
    val a11y = remember(tick) { Perms.isA11yOn(ctx) }
    val battery = remember(tick) { Perms.isIgnoringBattery(ctx) }
    val notify = remember(tick) { Perms.notificationsOn(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    if (step == 0) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            PageTitle(
                "电视安全锁",
                "装好后全自动运行：孩子看电视时，每 ${Prefs.intervalMin} 分钟弹一道题，答对才继续"
            )
            Spacer(Modifier.height(20.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // 电视横屏放两列，手机竖屏自动变一列
                val cardW = if (maxWidth > 880.dp) (maxWidth - 14.dp) / 2 else maxWidth
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PermItem(
                        width = cardW,
                        index = 1,
                        title = "允许悬浮窗",
                        desc = "必须。让孩子看到的是题目，而不是动画片",
                        ok = overlay,
                        required = true,
                        onClick = { Perms.openOverlay(ctx) }
                    )
                    PermItem(
                        width = cardW,
                        index = 2,
                        title = "开启无障碍",
                        desc = "强烈建议。拦住遥控器主页键，防止孩子跳过题目",
                        ok = a11y,
                        required = false,
                        onClick = { Perms.openA11y(ctx) }
                    )
                    PermItem(
                        width = cardW,
                        index = 3,
                        title = "允许后台常驻",
                        desc = "必须。不然系统会把计时器杀掉，就不弹题了",
                        ok = battery,
                        required = true,
                        onClick = { Perms.openBattery(ctx) }
                    )
                    PermItem(
                        width = cardW,
                        index = 4,
                        title = "通知权限",
                        desc = "可选。用来显示运行状态，不影响弹题",
                        ok = notify,
                        required = false,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Perms.openNotificationSettings(ctx)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SurfaceNote(
                "小米 / 海信 / 创维等国产电视还要多做一步：到「设置 → 应用管理 → 电视安全锁」里，" +
                        "把「自启动」「后台运行」「省电策略」都放行，否则重启后可能不生效。"
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (overlay && battery) "下一步：设置家长密码" else "已开启 1、3 项后继续",
                enabled = overlay && battery,
                modifier = Modifier.width(340.dp),
                onClick = { step = 1 }
            )
        }
    } else {
        PinSetupScreen(onFinish = onFinish)
    }
}

/** 单项权限卡片 */
@Composable
private fun PermItem(
    width: androidx.compose.ui.unit.Dp,
    index: Int,
    title: String,
    desc: String,
    ok: Boolean,
    required: Boolean,
    onClick: () -> Unit
) {
    FocusCard(onClick = onClick, modifier = Modifier.width(width), selected = ok) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(34.dp)
                    .background(
                        if (ok) OkGreen else BrandBlue.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (ok) "✓" else "$index",
                    color = if (ok) Color.White else BrandBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                    if (required) {
                        Spacer(Modifier.width(6.dp))
                        Text("必须", fontSize = 11.sp, color = WarnRed)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(desc, fontSize = 12.sp, color = InkGray, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (ok) "已开启" else "点此去开启 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (ok) OkGreen else BrandBlue
                )
            }
        }
    }
}

/** 灰底提示条 */
@Composable
fun SurfaceNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF7E6), RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 18.sp)
    }
}

/** 设置家长密码（输两遍） */
@Composable
private fun PinSetupScreen(onFinish: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(0) }   // 0=第一次输入 1=确认
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle(
            if (stage == 0) "设置家长密码" else "再输一次确认",
            if (stage == 0) "4 位数字。以后改设置、退出答题都要用它，别告诉孩子"
            else "两次输入需一致"
        )
        Spacer(Modifier.height(10.dp))
        if (error.isNotEmpty()) {
            Text(error, color = WarnRed, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))

        PinPad(
            value = input,
            onValueChange = { v ->
                input = v
                if (v.length == 4) {
                    if (stage == 0) {
                        first = v
                        input = ""
                        stage = 1
                        error = ""
                    } else {
                        if (v == first) {
                            Prefs.setPin(v)
                            Prefs.setupDone = true
                            onFinish()
                        } else {
                            error = "两次输入不一致，请重新设置"
                            input = ""
                            first = ""
                            stage = 0
                        }
                    }
                }
            },
            onConfirm = { }
        )
    }
}
