package com.tvsafelock.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsafelock.app.license.License

/**
 * 激活页：显示设备码 + 输入 12 位数字激活码
 */
@Composable
fun ActivationScreen(onBack: () -> Unit) {

    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var msgColor by remember { mutableStateOf(InkGray) }
    var licensed by remember { mutableStateOf(License.isLicensed()) }
    val daysLeft = remember { License.trialDaysLeft(ctx) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PageTitle(
                if (licensed) "已激活" else "激活电视安全锁",
                if (licensed) "感谢支持，功能永久有效" else "试用剩余 $daysLeft 天，激活后可永久使用"
            )

            Spacer(Modifier.height(20.dp))

            // 设备码
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("本机设备码", fontSize = 12.sp, color = InkGray)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        License.pretty(License.deviceCode(ctx)),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把这串数字发给卖家，换取 12 位激活码",
                        fontSize = 11.sp,
                        color = InkGray
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (licensed) {
                Text("此设备已完成激活 ✓", color = OkGreen, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(24.dp))
                PrimaryButton("返回", onClick = onBack, modifier = Modifier.width(220.dp))
            } else {

            // 激活码输入显示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val shown = input.padEnd(12, '·')
                Text(
                    shown.chunked(4).joinToString("  "),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkDark,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(10.dp))
            if (msg.isNotEmpty()) {
                Text(msg, color = msgColor, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))

            // 数字键盘
            NumberPad(
                onDigit = { d ->
                    if (input.length < 12) {
                        input += d
                        msg = ""
                    }
                },
                onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PrimaryButton(
                    text = "确认激活",
                    enabled = input.length == 12,
                    modifier = Modifier.width(240.dp),
                    onClick = {
                        if (License.activate(ctx, input)) {
                            licensed = true
                            msgColor = OkGreen
                            msg = "激活成功！"
                        } else {
                            msgColor = WarnRed
                            msg = "激活码不对，请核对后重试"
                            input = ""
                        }
                    }
                )
                FocusCard(onClick = onBack, modifier = Modifier.width(140.dp)) {
                    Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                        Text("返回", fontSize = 16.sp, color = InkDark)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "提示：激活码与本机设备码绑定，换一台电视需要重新获取。",
                fontSize = 11.sp,
                color = InkGray
            )
            }
        }
    }
}

/** 0-9 数字键盘，遥控器方向键 + OK 键操作 */
@Composable
private fun NumberPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("删除", "0", "·")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val enabled = key != "·"
                    FocusCard(
                        enabled = enabled,
                        onClick = {
                            when (key) {
                                "删除" -> onDelete()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.width(84.dp)
                    ) {
                        Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                            Text(
                                key,
                                fontSize = if (key == "删除") 14.sp else 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (enabled) InkDark else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}
