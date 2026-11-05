package com.tvsafelock.app.data

/**
 * 一道题
 * @param grade   所属年级 1~6
 * @param subject 科目：chinese / math / english
 * @param title   题干
 * @param options 四个选项
 * @param answer  正确选项下标（0~3）
 */
data class Question(
    val grade: Int,
    val subject: String,
    val title: String,
    val options: List<String>,
    val answer: Int
) {
    val subjectName: String
        get() = when (subject) {
            Prefs.SUBJECT_CHINESE -> "语文"
            Prefs.SUBJECT_ENGLISH -> "英语"
            else -> "数学"
        }

    val gradeName: String get() = "${grade}年级"
}
