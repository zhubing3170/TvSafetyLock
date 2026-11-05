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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.tvsafelock.app.data.Question
import kotlinx.coroutines.delay

/**
 * 答题界面（深色，电视上看不刺眼）
 *
 * @param phase 0=答题中 1=答对 2=答错
 * @param chosen 孩子选的那一项（-1 表示还没选）
 */
@Composable
fun QuizScreen(
    question: Question,
    phase: Int,
    chosen: Int,
    wrongCount: Int,
    onAnswer: (Int) -> Unit,
    onParent: () -> Unit
) {

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(question.title, question.options) {
        delay(180)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // 拿不到焦点也不影响触摸操作
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ============ 顶部信息条 ============
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .background(BrandBlue, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${question.subjectName} · ${question.gradeName}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (phase) {
                            1 -> "答对啦"
                            2 -> "答错了，再想想"
                            else -> if (wrongCount > 0) "第 ${wrongCount + 1} 次尝试" else "答对才能继续看电视"
                        },
                        color = when (phase) {
                            1 -> Color(0xFF4ADE80)
                            2 -> Color(0xFFF87171)
                            else -> Color(0xFF8FA3C8)
                        },
                        fontSize = 14.sp
                    )
                }

                // 家长出口：小按钮，孩子一般不会注意
                FocusCard(dark = true, onClick = onParent, modifier = Modifier.width(88.dp)) {
                    Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                        Text("家长", color = Color(0xFF8FA3C8), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ============ 题干 ============
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    question.title,
                    color = Color.White,
                    fontSize = when {
                        question.title.length <= 10 -> 52.sp
                        question.title.length <= 18 -> 42.sp
                        else -> 32.sp
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 56.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ============ 四个选项 ============
            Column(
                modifier = Modifier.widthIn(max = 900.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                question.options.chunked(2).forEachIndexed { rowIdx, rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        rowOptions.forEachIndexed { colIdx, text ->
                            val index = rowIdx * 2 + colIdx
                            val isChosen = chosen == index
                            val isAnswer = index == question.answer

                            // 答对后全部变绿；答错后把正确答案标绿、选错的标红
                            val bg = when {
                                phase == 1 -> Color(0xFF14532D)
                                phase == 2 && isAnswer -> Color(0xFF14532D)
                                phase == 2 && isChosen -> Color(0xFF7F1D1D)
                                else -> NightCard
                            }

                            val letter = listOf("A", "B", "C", "D").getOrElse(index) { "?" }

                            val modifier = if (index == 0) {
                                Modifier.weight(1f).focusRequester(focusRequester)
                            } else {
                                Modifier.weight(1f)
                            }

                            FocusCard(
                                dark = !(phase == 1 || (phase == 2 && (isAnswer || isChosen))),
                                enabled = phase == 0,
                                onClick = { onAnswer(index) },
                                modifier = modifier
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .padding(vertical = 18.dp, horizontal = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(30.dp)
                                                .background(Color(0xFF2B3D63), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(letter, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text,
                                            color = Color.White,
                                            fontSize = if (text.length <= 8) 22.sp else 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "遥控器：方向键选择 · OK 键确认",
                color = Color(0xFF5B6E93),
                fontSize = 12.sp
            )
        }
    }
}
