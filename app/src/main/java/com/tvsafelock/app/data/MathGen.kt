package com.tvsafelock.app.data

import kotlin.random.Random

/**
 * 数学题自动生成器。
 * 好处：不用存题库，题目永远不会重复，孩子背不了答案。
 * 按年级控难度，全部保证答案是「非负整数」，方便做 4 个选项。
 */
object MathGen {

    fun generate(grade: Int): Question {
        val g = grade.coerceIn(1, 6)
        repeat(40) {
            val built = build(g) ?: return@repeat
            val (title, ans) = built
            val opts = buildOptions(ans)
            val idx = opts.indexOfFirst { it == ans.toString() }
            if (idx >= 0) {
                return Question(g, Prefs.SUBJECT_MATH, title, opts, idx)
            }
        }
        // 兜底（几乎走不到）
        return Question(g, Prefs.SUBJECT_MATH, "1 + 1 = ?", listOf("2", "3", "1", "4"), 0)
    }

    /** 生成 4 个不重复选项，正确答案随机落在其中一位 */
    private fun buildOptions(ans: Int): List<String> {
        val pool = linkedSetOf(ans.toString())
        val deltas = intArrayOf(-20, -10, -9, -5, -3, -2, -1, 1, 2, 3, 5, 9, 10, 20)
        var guard = 0
        while (pool.size < 4 && guard++ < 200) {
            val v = ans + deltas[Random.nextInt(deltas.size)]
            if (v >= 0) pool.add(v.toString())
        }
        // 极端情况下补几个递增数
        var extra = ans + 1
        while (pool.size < 4) {
            pool.add((extra++).toString())
        }
        return pool.toList().shuffled()
    }

    /** 返回 题干 to 答案 */
    private fun build(grade: Int): Pair<String, Int>? {
        return when (grade) {
            1 -> {
                val a = Random.nextInt(1, 11)
                val b = Random.nextInt(1, 11)
                if (Random.nextBoolean()) {
                    "$a + $b = ?" to (a + b)
                } else {
                    val big = maxOf(a, b)
                    val small = minOf(a, b)
                    "$big - $small = ?" to (big - small)
                }
            }

            2 -> when (Random.nextInt(3)) {
                0 -> {
                    val a = Random.nextInt(10, 90); val b = Random.nextInt(10, 89)
                    "$a + $b = ?" to (a + b)
                }
                1 -> {
                    val a = Random.nextInt(30, 100); val b = Random.nextInt(10, 29)
                    "$a - $b = ?" to (a - b)
                }
                else -> {
                    val a = Random.nextInt(2, 10); val b = Random.nextInt(2, 10)
                    "$a × $b = ?" to (a * b)
                }
            }

            3 -> when (Random.nextInt(3)) {
                0 -> {
                    val a = Random.nextInt(100, 500); val b = Random.nextInt(100, 499)
                    "$a + $b = ?" to (a + b)
                }
                1 -> {
                    val a = Random.nextInt(11, 100); val b = Random.nextInt(2, 10)
                    "$a × $b = ?" to (a * b)
                }
                else -> {
                    val b = Random.nextInt(2, 10); val q = Random.nextInt(2, 20)
                    "${b * q} ÷ $b = ?" to q
                }
            }

            4 -> when (Random.nextInt(3)) {
                0 -> {
                    val a = Random.nextInt(100, 900); val b = Random.nextInt(11, 99)
                    "$a + $b = ?" to (a + b)
                }
                1 -> {
                    val a = Random.nextInt(11, 99); val b = Random.nextInt(2, 10)
                    "$a × $b = ?" to (a * b)
                }
                else -> {
                    // a + b × c
                    val a = Random.nextInt(20, 200); val b = Random.nextInt(2, 10); val c = Random.nextInt(2, 10)
                    "$a + $b × $c = ?" to (a + b * c)
                }
            }

            5 -> when (Random.nextInt(3)) {
                0 -> {
                    val a = Random.nextInt(11, 60); val b = Random.nextInt(2, 10); val c = Random.nextInt(10, 99)
                    "$a × $b + $c = ?" to (a * b + c)
                }
                1 -> {
                    val a = Random.nextInt(500, 5000); val b = Random.nextInt(100, 999)
                    "$a - $b = ?" to (a - b)
                }
                else -> {
                    val b = Random.nextInt(11, 40); val q = Random.nextInt(3, 30)
                    "${b * q} ÷ $b = ?" to q
                }
            }

            else -> when (Random.nextInt(3)) {
                0 -> {
                    // 百分数题：保证整除
                    val a = Random.nextInt(2, 21) * 20            // 40~400
                    val p = intArrayOf(5, 10, 20, 25, 50)[Random.nextInt(5)]
                    val ans = a * p / 100
                    if (a * p % 100 != 0) return null
                    "${a} 的 $p% 是多少？" to ans
                }
                1 -> {
                    val a = Random.nextInt(2, 12); val b = Random.nextInt(2, 12); val c = Random.nextInt(2, 10)
                    "($a + $b) × $c = ?" to ((a + b) * c)
                }
                else -> {
                    val a = Random.nextInt(20, 200); val b = Random.nextInt(11, 99); val c = Random.nextInt(2, 10)
                    "$a + $b × $c = ?" to (a + b * c)
                }
            }
        }
    }
}
