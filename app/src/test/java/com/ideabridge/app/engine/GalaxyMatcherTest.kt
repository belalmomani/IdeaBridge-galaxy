package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyMatcherTest {
    private val matcher = GalaxyMatcher()
    private fun doc(id: Long, title: String, body: String? = null) = IdeaDoc(id, title, body)

    private val inbox = GalaxyDoc(1, "ملاحظاتي", emptyList())
    private val cooking = GalaxyDoc(
        2, "طبخ",
        listOf(doc(10, "وصفة كبسة بالدجاج"), doc(11, "طبخ المنسف للضيوف"), doc(12, "طريقة طبخ الأرز")),
    )
    private val computer = GalaxyDoc(
        3, "حاسوب",
        listOf(doc(20, "تثبيت نظام التشغيل على الحاسوب"), doc(21, "شراء ذاكرة للحاسوب"), doc(22, "برمجة تطبيق")),
    )
    private val trips = GalaxyDoc(4, "رحلات", listOf(doc(30, "رحلة إلى البحر"), doc(31, "حجز فندق")))
    private val kitchen = GalaxyDoc(
        5, "مطبخ الأسرة",
        listOf(doc(40, "شراء أدوات الطبخ"), doc(41, "وصفات الطبخ للأسرة")),
    )
    private val all = listOf(inbox, cooking, computer, trips, kitchen)

    @Test fun cookingNoteIsSuggestedForTheCookingGalaxy() {
        val m = matcher.suggestFor(doc(100, "طبخ الدجاج بالفرن"), currentGalaxyId = 1, galaxies = all)
        assertNotNull(m)
        assertEquals(2L, m!!.galaxyId)
        assertTrue(m.relevance >= 60)
        assertTrue("words=${m.matchedWords}", m.matchedWords.contains("طبخ"))
    }

    @Test fun computerNoteIsSuggestedForTheComputerGalaxy() {
        val m = matcher.suggestFor(doc(101, "ذاكرة الحاسوب الجديدة", "نريد زيادة الذاكرة"), 1, all)
        assertEquals(3L, m?.galaxyId)
    }

    @Test fun nameInTheTitleBeatsNameOnlyInTheBody() {
        val title = matcher.suggestFor(doc(102, "حاسوب جديد"), 1, all)!!
        val body = matcher.suggestFor(doc(103, "شيء مهم", "أحتاج إلى حاسوب"), 1, all)!!
        assertEquals(3L, title.galaxyId)
        assertEquals(3L, body.galaxyId)
        assertTrue(title.relevance > body.relevance)
    }

    @Test fun unrelatedNoteGetsNoSuggestion() {
        assertNull(matcher.suggestFor(doc(104, "ماذا أشتري غدًا"), 1, all))
    }

    @Test fun noteAlreadyInTheBestGalaxyIsNotMoved() {
        assertNull(matcher.suggestFor(doc(100, "طبخ الدجاج بالفرن"), currentGalaxyId = 2, galaxies = all))
    }

    @Test fun nothingToSuggestWithASingleGalaxyOrNone() {
        assertNull(matcher.suggestFor(doc(1, "طبخ"), 2, listOf(cooking)))
        assertNull(matcher.suggestFor(doc(1, "طبخ"), 2, emptyList()))
    }

    @Test fun diacriticsAndDefiniteArticleStillMatchTheName() {
        val m = matcher.suggestFor(doc(105, "الطَّبْخُ على الحطب"), 1, all)
        assertEquals(2L, m?.galaxyId)
    }

    @Test fun relatedGalaxiesAreLinkedByTheirText() {
        val links = matcher.galaxyLinks(all)
        val link = links.single { it.key == pairKey(2, 5) }
        assertTrue(link.sharedTerms.contains("طبخ"))
        assertTrue(link.score >= GalaxyMatcher.GALAXY_LINK_THRESHOLD)
    }

    @Test fun unrelatedGalaxiesAreNotLinked() {
        val links = matcher.galaxyLinks(all)
        assertTrue(links.none { it.firstId == 4L || it.secondId == 4L })
        assertTrue(links.none { it.key == pairKey(2, 3) })
    }

    @Test fun emptyGalaxiesAreHandledSafely() {
        assertTrue(matcher.galaxyLinks(emptyList()).isEmpty())
        assertTrue(matcher.galaxyLinks(listOf(inbox)).isEmpty())
    }
}
