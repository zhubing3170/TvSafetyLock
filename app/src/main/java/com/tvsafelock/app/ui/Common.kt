package com.tvsafelock.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================= 配色 =======================
val BrandBlue = Color(0xFF1D4ED8)
val BrandBlueDeep = Color(0xFF1E3A8A)
val PageBg = Color(0xFFF5F7FB)
val InkDark = Color(0xFF111827)
val InkGray = Color(0xFF6B7280)
val OkGreen = Color(0xFF16A34A)
val WarnRed = Color(0xFFDC2626)
val LineGray = Color(0xFFE5E7EB)
val NightBg = Color(0xFF0B1220)
val NightCard = Color(0xFF16213A)

/**
 * 遥控器友好的卡片：获得焦点时边框变蓝加粗，和电视原生 UI 的操作习惯一致
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    dark: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val bg = when {
        focused -> if (dark) Color(0xFF1E3A8A) else BrandBlue.copy(alpha = 0.10f)
        selected -> if (dark) NightCard else BrandBlue.copy(alpha = 0.07f)
        else -> if (dark) NightCard else Color.White
    }
    val borderColor = when {
        focused -> BrandBlue
        selected -> BrandBlue.copy(alpha = 0.55f)
        else -> if (dark) Color(0xFF2C3E63) else LineGray
    }
    val borderWidth = if (focused) 3.dp else if (selected) 2.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) bg else bg.copy(alpha = 0.5f),
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled) { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

/** 蓝色主按钮（遥控器 OK 键可触发） */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (enabled) BrandBlue else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 页面顶部标题 */
@Composable
fun PageTitle(text: String, sub: String? = null, dark: Boolean = false) {
    Column {
        Text(
            text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (dark) Color.White else InkDark
        )
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(sub, fontSize = 13.sp, color = if (dark) Color(0xFF9CA9C4) else InkGray)
        }
    }
}

/** 小节标题 */
@Composable
fun SectionLabel(text: String, dark: Boolean = false) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = if (dark) Color(0xFF9CA9C4) else InkGray
    )
}

/**
 * 4 位密码输入盘，完全用遥控器方向键操作
 * 布局：1 2 3 / 4 5 6 / 7 8 9 / 删除 0 确定
 */
@Composable
fun PinPad(
    value: String,
    maxLen: Int = 4,
    onValueChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    dark: Boolean = false
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "删", "0", "定")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 密码圆点
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            repeat(maxLen) { i ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            if (i < value.length) BrandBlue else (if (dark) Color(0xFF2C3E63) else LineGray),
                            CircleShape
                        )
                )
            }
        }

        // 键盘（每行 3 个）
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        FocusCard(
                            dark = dark,
                            onClick = {
                                when (key) {
                                    "删" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                    "定" -> if (value.length == maxLen) onConfirm(value)
                                    else -> if (value.length < maxLen) onValueChange(value + key)
                                }
                            },
                            modifier = Modifier.width(84.dp)
                        ) {
                            Box(Modifier.height(46.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    key,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (dark) Color.White else InkDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
