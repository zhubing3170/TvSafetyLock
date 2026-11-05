package com.tvsafelock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsafelock.app.data.Prefs
import kotlinx.coroutines.delay

/**
 * 打开设置前的密码门。
 * 孩子拿到遥控器也进不来 —— 这是他没法改设置的关键。
 */
@Composable
fun PinGateScreen(onSuccess: () -> Unit, onCancel: (() -> Unit)? = null) {

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var fails by remember { mutableStateOf(0) }
    var lockSeconds by remember { mutableStateOf(0) }

    // 错误 5 次后冷却 60 秒，防止孩子乱按试出来
    LaunchedEffect(lockSeconds) {
        if (lockSeconds > 0) {
            delay(1000)
            lockSeconds -= 1
            if (lockSeconds == 0) {
                fails = 0
                error = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        PageTitle("家长验证", "输入 4 位密码后才能修改设置")
        Spacer(Modifier.height(14.dp))

        when {
            lockSeconds > 0 -> Text(
                "错误次数过多，$lockSeconds 秒后再试",
                color = WarnRed,
                fontSize = 16.sp
            )
            error.isNotEmpty() -> Text(error, color = WarnRed, fontSize = 16.sp)
        }

        Spacer(Modifier.height(10.dp))

        if (lockSeconds == 0) {
            PinPad(
                value = input,
                onValueChange = { v ->
                    input = v
                    if (v.length == 4) {
                        if (!Prefs.hasPin()) {
                            // 还没设过密码（异常情况）：直接放行并提示去设置
                            onSuccess()
                        } else if (Prefs.checkPin(v)) {
                            onSuccess()
                        } else {
                            input = ""
                            fails += 1
                            error = "密码不对，再试一次"
                            if (fails >= 5) {
                                lockSeconds = 60
                                input = ""
                            }
                        }
                    }
                },
                onConfirm = { }
            )
        }

        if (onCancel != null) {
            Spacer(Modifier.height(22.dp))
            FocusCard(onClick = onCancel, modifier = Modifier.width(160.dp)) {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    Text("取消", color = InkGray, fontSize = 15.sp)
                }
            }
        }
    }
}
