package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GalaxyLayoutTest {
    private fun dist(m: Map<Long, GalaxyLayout.Point>, a: Long, b: Long): Float {
        val p = m.getValue(a); val q = m.getValue(b)
        return hypot(p.x - q.x, p.y - q.y)
    }

    @Test fun emptyAndSingle() {
        assertTrue(GalaxyLayout.compute(emptyList(), emptyList()).isEmpty())
        assertEquals(GalaxyLayout.Point(0f, 0f), GalaxyLayout.compute(listOf(5L), emptyList())[5L])
    }

    @Test fun layoutIsDeterministicAndIndependentOfInputOrder() {
        val a = GalaxyLayout.compute(listOf(1, 2, 3), listOf(1L to 2L))
        val b = GalaxyLayout.compute(listOf(3, 2, 1), listOf(2L to 1L))
        assertEquals(a, b)
    }

    @Test fun pointsStayInsideTheUnitSquare() {
        val m = GalaxyLayout.compute((1L..6L).toList(), listOf(1L to 2L, 1L to 3L, 1L to 4L))
        assertEquals(6, m.size)
        m.values.forEach { assertTrue(abs(it.x) <= 1.0001f && abs(it.y) <= 1.0001f) }
        assertTrue(m.values.any { abs(it.x) > 0.999f || abs(it.y) > 0.999f })
    }

    @Test fun planetsDoNotOverlap() {
        val star = GalaxyLayout.compute((1L..6L).toList(), listOf(1L to 2L, 1L to 3L, 1L to 4L))
        val chain = GalaxyLayout.compute((1L..30L).toList(), (1L..29L).map { it to it + 1 })
        for (m in listOf(star, chain)) {
            val ids = m.keys.toList()
            for (i in ids.indices) for (j in i + 1 until ids.size) assertTrue(dist(m, ids[i], ids[j]) > 0.05f)
        }
    }

    @Test fun linkedNotesEndUpCloserThanUnlinkedOnes() {
        val m = GalaxyLayout.compute((1L..6L).toList(), listOf(1L to 2L, 1L to 3L, 1L to 4L))
        val linkedMax = listOf(dist(m, 1, 2), dist(m, 1, 3), dist(m, 1, 4)).max()
        val unlinkedMin = listOf(dist(m, 5, 6), dist(m, 2, 5)).min()
        assertTrue("linked=$linkedMax unlinked=$unlinkedMin", linkedMax < unlinkedMin)
    }

    @Test fun edgesToUnknownNotesAreIgnored() {
        val m = GalaxyLayout.compute(listOf(1, 2), listOf(1L to 99L, 2L to 2L))
        assertEquals(2, m.size)
    }

    @Test fun planetRadiusGrowsWithTextAndLinksAndIsCapped() {
        val small = GalaxyLayout.planetRadius(0, 0)
        assertTrue(small < GalaxyLayout.planetRadius(1000, 0))
        assertTrue(GalaxyLayout.planetRadius(1000, 0) < GalaxyLayout.planetRadius(1000, 5))
        assertEquals(32f, GalaxyLayout.planetRadius(1_000_000, 100), 0.001f)
    }
}
