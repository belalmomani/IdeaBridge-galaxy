package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkEngineTest {
    private val engine = LinkEngine()
    private fun doc(id: Long, title: String, body: String? = null) = IdeaDoc(id, title, body)

    private val chickens = listOf(
        doc(1, "تربية الدجاج البلدي"),
        doc(2, "تربية الدجاج للبيض"),
        doc(3, "تربية الدجاج"),
        doc(4, "تعلم البرمجة"),
    )

    @Test fun nearlyIdenticalNotesScoreHigh() {
        val score = engine.similarity(doc(1, "تربية الدجاج البلدي في المنزل"), doc(2, "تربية الدجاج البلدي"))
        assertTrue("score=$score", score >= 60)
    }

    @Test fun unrelatedNotesScoreZero() {
        assertEquals(0, engine.similarity(doc(1, "تربية الدجاج"), doc(2, "تعلم البرمجة")))
    }

    @Test fun diacriticsDoNotChangeTheScore() {
        val score = engine.similarity(doc(1, "تَرْبِيَةُ الدَّجَاجِ"), doc(2, "تربية الدجاج"))
        assertTrue("score=$score", score >= 95)
    }

    @Test fun veryShortNotesAreHandledSafely() {
        assertEquals(0, engine.similarity(doc(1, "و"), doc(2, "في")))
        assertTrue(engine.suggest(listOf(doc(1, "و"), doc(2, "في"))).isEmpty())
        assertTrue(engine.suggest(emptyList()).isEmpty())
        assertTrue(engine.suggest(listOf(doc(1, "تربية الدجاج"))).isEmpty())
    }

    @Test fun aWordCommonToMostNotesDoesNotLinkUnrelatedNotes() {
        val notes = listOf("تربية الدجاج", "تعلم البرمجة", "سفر الصيف", "شراء سيارة", "قراءة كتب", "رياضة المشي")
            .mapIndexed { i, t -> doc(i + 1L, "مشروع $t") }
        assertTrue(engine.suggest(notes).isEmpty())
    }

    @Test fun aNoteIsNeverComparedWithItself() {
        assertEquals(0, engine.similarity(doc(1, "تربية الدجاج"), doc(1, "تربية الدجاج")))
        val same = listOf(doc(1, "تربية الدجاج البلدي"), doc(1, "تربية الدجاج البلدي"))
        assertTrue(engine.suggest(same).isEmpty())
        engine.suggest(chickens).forEach { assertTrue(it.firstId != it.secondId) }
    }

    @Test fun eachPairAppearsOnceRegardlessOfInputOrder() {
        val forward = engine.suggest(chickens)
        val backward = engine.suggest(chickens.reversed())
        assertEquals(forward.size, forward.map { it.key }.distinct().size)
        assertEquals(forward.map { it.key }.toSet(), backward.map { it.key }.toSet())
    }

    @Test fun resultsAreSortedByScore() {
        val scores = engine.suggest(chickens).map { it.score }
        assertTrue(scores.size >= 2)
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test fun existingAndRejectedPairsAreExcludedWithoutTouchingTheInput() {
        val all = engine.suggest(chickens)
        val first = all.first()
        val withoutExisting = engine.suggest(chickens, existingPairs = setOf(first.key))
        assertFalse(withoutExisting.any { it.key == first.key })
        val withoutRejected = engine.suggest(chickens, rejectedKeys = setOf(first.key))
        assertFalse(withoutRejected.any { it.key == first.key })
        assertEquals(all.size - 1, withoutRejected.size)
        assertEquals(4, chickens.size) // لم تتغير قائمة الملاحظات
    }

    @Test fun reasonComesFromTheActualSharedWords() {
        val a = doc(1, "تربية الدجاج")
        val b = doc(2, "تغذية الدجاج")
        val s = engine.suggest(listOf(a, b)).single()
        assertEquals(listOf("الدجاج"), s.sharedTerms)
        assertEquals(engine.similarity(a, b), s.score)
        assertEquals(LinkStrength.MEDIUM, s.strength)

        val other = engine.suggest(listOf(doc(1, "إدارة مصروفات المزرعة"), doc(2, "حساب مصروفات المزرعة"))).single()
        assertEquals(setOf("مصروفات", "المزرعة"), other.sharedTerms.toSet())
    }

    @Test fun strengthFollowsTheScore() {
        val strong = engine.suggest(chickens).first { it.key == pairKey(1, 3) }
        assertEquals(LinkStrength.STRONG, strong.strength)
        val medium = engine.suggest(chickens).first { it.key == pairKey(1, 2) }
        assertEquals(LinkStrength.MEDIUM, medium.strength)
    }

    @Test fun thresholdAndCapAreConfigurable() {
        assertEquals(2, LinkEngine(EngineConfig(maxSuggestions = 2)).suggest(chickens).size)
        assertTrue(LinkEngine(EngineConfig(similarityThreshold = 90)).suggest(chickens).isEmpty())
    }
}
