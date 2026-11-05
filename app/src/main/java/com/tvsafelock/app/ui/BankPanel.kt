package com.tvsafelock.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsafelock.app.data.Prefs
import com.tvsafelock.app.data.QuestionUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页里的「在线题库」区块。
 * 全部操作都能用遥控器完成：三个卡片 —— 立即更新 / 自动更新开关 / 恢复内置。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var revision by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val summary = remember(revision) { QuestionUpdate.summary(ctx) }
    val configured = remember { QuestionUpdate.isConfigured() }
    val auto = remember(revision) { Prefs.bankAuto }

    SectionLabel("在线题库")
    Spacer(Modifier.height(8.dp))
    Text(summary, fontSize = 13.sp, color = InkDark)
    if (!configured) {
        Spacer(Modifier.height(6.dp))
        Text(
            "还没配置更新地址。用 GitHub Actions 打包会自动填；本地打包请在 gradle.properties 里填 questionBankUrl。",
            fontSize = 11.sp,
            color = WarnRed
        )
    }
    if (status.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(status, fontSize = 12.sp, color = if (busy) InkGray else BrandBlue)
    }
    Spacer(Modifier.height(10.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FocusCard(
            onClick = {
                if (!busy) {
                    busy = true
                    status = "正在检查更新…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            QuestionUpdate.check(ctx, force = true)
                        }
                        status = result.message
                        busy = false
                        revision++
                    }
                }
            },
            modifier = Modifier.width(190.dp)
        ) {
            Column {
                Text("立即更新题库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                Text("从 GitHub 拉最新题", fontSize = 11.sp, color = InkGray)
            }
        }

        FocusCard(
            onClick = {
                Prefs.bankAuto = !auto
                status = if (Prefs.bankAuto) "已开启：每次开机自动检查（每天最多一次）" else "已关闭自动检查"
                revision++
            },
            selected = auto,
            modifier = Modifier.width(180.dp)
        ) {
            Column {
                Text(
                    if (auto) "自动更新：开" else "自动更新：关",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (auto) OkGreen else InkGray
                )
                Text("点此切换", fontSize = 11.sp, color = InkGray)
            }
        }

        FocusCard(
            onClick = {
                val result = QuestionUpdate.restoreBuiltIn(ctx)
                status = result.message
                revision++
            },
            modifier = Modifier.width(180.dp)
        ) {
            Column {
                Text("恢复内置题库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                Text("在线题库出问题时用", fontSize = 11.sp, color = InkGray)
            }
        }
    }
}
