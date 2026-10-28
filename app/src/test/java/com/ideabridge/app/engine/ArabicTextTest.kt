package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTextTest {
    private fun norms(text: String) = ArabicText.tokens(text).map { it.norm }

    @Test fun diacriticsAreIgnoredForComparisonButOriginalIsKept() {
        val tokens = ArabicText.tokens("تَرْبِيَةُ الدَّجَاجِ")
        assertEquals(listOf("تربية", "دجاج"), tokens.map { it.norm })
        assertEquals("الدَّجَاجِ", tokens[1].original)
    }

    @Test fun tatweelIsRemoved() {
        assertEquals(listOf("دجاج"), norms("الدجـــاج"))
    }

    @Test fun alefVariantsAreUnified() {
        assertEquals(listOf("افكار", "ادارة"), norms("أفكار إدارة"))
    }

    @Test fun stopWordsAreDropped() {
        assertEquals(listOf("دجاج"), norms("هذا في الدجاج على"))
    }

    @Test fun punctuationIsIgnored() {
        assertEquals(listOf("دجاج", "بيض", "حليب"), norms("دجاج، بيض! (حليب)؟"))
    }

    @Test fun prefixStrippingIsConservative() {
        assertEquals("دجاج", ArabicText.stripPrefix("الدجاج"))
        assertEquals("مصروفات", ArabicText.stripPrefix("والمصروفات"))
        // كلمات لا يجوز تغييرها: يبقى بعد البادئة أقل من ثلاثة أحرف
        assertEquals("الله", ArabicText.stripPrefix("الله"))
        assertEquals("بالغ", ArabicText.stripPrefix("بالغ"))
        assertEquals("لبن", ArabicText.stripPrefix("لبن"))
    }

    @Test fun veryShortAndEmptyTextGivesNoTokens() {
        assertTrue(ArabicText.tokens("").isEmpty())
        assertTrue(ArabicText.tokens("و").isEmpty())
        assertTrue(ArabicText.tokens("في من").isEmpty())
    }
}
