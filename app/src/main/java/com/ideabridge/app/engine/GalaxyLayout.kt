package com.ideabridge.app.engine

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * تخطيط حتمي (نفس المدخلات = نفس النتيجة) للكواكب داخل المجرة: دفع بين كل الكواكب،
 * وجذب بين المرتبطة، وجاذبية خفيفة نحو المركز. النتيجة مُطبَّعة داخل [-1, 1] في المحورين.
 */
object GalaxyLayout {
    data class Point(val x: Float, val y: Float)

    fun compute(ids: List<Long>, edges: List<Pair<Long, Long>>, iterations: Int = 150): Map<Long, Point> {
        val sorted = ids.distinct().sorted()
        val n = sorted.size
        if (n == 0) return emptyMap()
        if (n == 1) return mapOf(sorted[0] to Point(0f, 0f))

        val index = HashMap<Long, Int>()
        sorted.forEachIndexed { i, id -> index[id] = i }
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        for (i in 0 until n) {
            val angle = i * 2.399963229728653 // الزاوية الذهبية
            val radius = 0.18 * sqrt(i + 1.0)
            x[i] = radius * cos(angle)
            y[i] = radius * sin(angle)
        }
        val links = HashSet<Pair<Int, Int>>()
        for ((a, b) in edges) {
            val ia = index[a] ?: continue
            val ib = index[b] ?: continue
            if (ia != ib) links.add(ia to ib)
        }

        val k = 1.0 / sqrt(n.toDouble())
        for (iter in 0 until iterations) {
            val temp = 0.12 * (1.0 - iter.toDouble() / iterations) + 0.003
            val dx = DoubleArray(n)
            val dy = DoubleArray(n)
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var ddx = x[i] - x[j]
                    var ddy = y[i] - y[j]
                    var d = hypot(ddx, ddy)
                    if (d < 1e-6) { // نقطتان متطابقتان: إزاحة حتمية صغيرة
                        ddx = 0.001 * (i + 1)
                        ddy = 0.001 * (j + 1)
                        d = hypot(ddx, ddy)
                    }
                    val rep = k * k / d
                    dx[i] += ddx / d * rep; dy[i] += ddy / d * rep
                    dx[j] -= ddx / d * rep; dy[j] -= ddy / d * rep
                }
            }
            for ((a, b) in links) {
                val ddx = x[a] - x[b]
                val ddy = y[a] - y[b]
                val d = maxOf(hypot(ddx, ddy), 1e-6)
                val att = d * d / k
                dx[a] -= ddx / d * att; dy[a] -= ddy / d * att
                dx[b] += ddx / d * att; dy[b] += ddy / d * att
            }
            for (i in 0 until n) {
                dx[i] -= x[i] * 0.4
                dy[i] -= y[i] * 0.4
                val len = hypot(dx[i], dy[i])
                if (len > 0.0) {
                    val m = min(len, temp) / len
                    x[i] += dx[i] * m
                    y[i] += dy[i] * m
                }
            }
        }

        val mx = x.average()
        val my = y.average()
        for (i in 0 until n) { x[i] -= mx; y[i] -= my }
        var maxAbs = 0.0
        for (i in 0 until n) maxAbs = maxOf(maxAbs, kotlin.math.abs(x[i]), kotlin.math.abs(y[i]))
        if (maxAbs < 1e-9) maxAbs = 1.0
        val result = LinkedHashMap<Long, Point>()
        for (i in 0 until n) result[sorted[i]] = Point((x[i] / maxAbs).toFloat(), (y[i] / maxAbs).toFloat())
        return result
    }

    /** نصف قطر الكوكب (بوحدة نسبية): يكبر مع طول النص وعدد الروابط. */
    fun planetRadius(textLength: Int, linkCount: Int): Float =
        12f + minOf(textLength / 200f, 8f) + minOf(linkCount, 8) * 1.5f
}
