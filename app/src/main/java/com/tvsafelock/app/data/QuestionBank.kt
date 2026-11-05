package com.tvsafelock.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 题库。
 * - 数学：走 MathGen 现算，题目无限不重复
 * - 语文 / 英语：优先读「在线更新下载下来的题库」，没有或者坏了就自动回退 assets 内置题库
 *
 * 想加题？两种办法：
 *  ① 在线（推荐，不用重新装 APK）：改 GitHub 仓库里的 question-bank/questions.json，
 *     把 version +1 后 push，电视上点「立即更新题库」即可。
 *  ② 离线：编辑 app/src/main/assets/questions.json 再重新打包。
 */
object QuestionBank {

    private val cache = HashMap<String, List<Question>>()
    private val recent = ArrayDeque<String>()

    /** 在线题库替换成功后调用，清掉内存缓存，下一道题就用新的 */
    @Synchronized
    fun invalidate() {
        cache.clear()
        recent.clear()
    }

    /** 读取题库文本：在线版优先，校验不过就直接回退内置版 */
    private fun readBank(context: Context, online: Boolean): String {
        if (online) {
            val f = QuestionUpdate.bankFile(context)
            try {
                val text = f.readText(Charsets.UTF_8)
                if (QuestionUpdate.validate(text) != null) return text
                // 文件坏了，删掉它，免得每次都白读一遍
                f.delete()
            } catch (e: Exception) {
                // 读失败就当没有在线题库
            }
        }
        return context.assets.open("questions.json").bufferedReader().use { it.readText() }
    }

    /** 把一段 JSON 解析进缓存，返回是否解析出了题 */
    private fun parse(text: String): Boolean {
        val root = JSONObject(text)
        for (subject in listOf(Prefs.SUBJECT_CHINESE, Prefs.SUBJECT_ENGLISH)) {
            val arr = root.optJSONArray(subject) ?: continue
            val list = ArrayList<Question>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val oa = o.optJSONArray("o") ?: continue
                val opts = ArrayList<String>()
                for (j in 0 until oa.length()) opts.add(oa.optString(j, ""))
                if (opts.size < 2) continue
                val title = o.optString("q", "")
                if (title.isBlank()) continue
                list.add(
                    Question(
                        grade = o.optInt("g", 1).coerceIn(1, 6),
                        subject = subject,
                        title = title,
                        options = opts,
                        answer = o.optInt("a", 0).coerceIn(0, opts.size - 1)
                    )
                )
            }
            if (list.isNotEmpty()) cache[subject] = list
        }
        return cache.isNotEmpty()
    }

    @Synchronized
    private fun load(context: Context) {
        if (cache.isNotEmpty()) return
        try {
            val onlineExists = QuestionUpdate.bankFile(context).exists()
            val ok = parse(readBank(context, onlineExists))
            if (!ok && onlineExists) {
                // 在线版解析出了 0 题 → 回退内置版
                cache.clear()
                parse(readBank(context, false))
            }
        } catch (e: Exception) {
            // 题库文件损坏时不要崩，数学题兜底
            cache.clear()
        }
    }

    /** 从家长的「已选科目」里随机抽一科出题 */
    fun pickAny(context: Context, grade: Int, subjects: Set<String>): Question {
        val pool = if (subjects.isEmpty()) listOf(Prefs.SUBJECT_MATH) else subjects.toList()
        val subject = pool.random()
        return pick(context, grade, subject)
    }

    fun pick(context: Context, grade: Int, subject: String): Question {
        if (subject == Prefs.SUBJECT_MATH) return MathGen.generate(grade)

        load(context)
        val all = cache[subject].orEmpty()
        val sameGrade = all.filter { it.grade == grade }
        val use = if (sameGrade.isNotEmpty()) sameGrade else all
        if (use.isEmpty()) return MathGen.generate(grade)   // 该科没题就去出数学

        var q = use.random()
        var guard = 0
        while (recent.contains(q.title) && guard++ < 12) {
            q = use.random()
        }
        recent.addLast(q.title)
        if (recent.size > 24) recent.removeFirst()
        return q
    }
}
