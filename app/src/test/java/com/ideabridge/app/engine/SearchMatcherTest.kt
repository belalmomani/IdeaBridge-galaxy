package com.ideabridge.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    @Test fun diacriticsAndTatweelDoNotMatter() {
        assertTrue(SearchMatcher.matches("طبخ", "الطَّبْخُ على الحطب"))
        assertTrue(SearchMatcher.matches("الدجاج", "تربيـــة الدجاج"))
    }

    @Test fun definiteArticleInTheQueryIsIgnored() {
        assertTrue(SearchMatcher.matches("الطبخ", "وصفة طبخ"))
    }

    @Test fun taaMarbutaAndHaaAreEquivalent() {
        assertTrue(SearchMatcher.matches("مدرسه", "المدرسة الجديدة"))
        assertTrue(SearchMatcher.matches("مدرسة", "مدرسه"))
    }

    @Test fun alefAndYaaVariantsAreEquivalent() {
        assertTrue(SearchMatcher.matches("اكل", "أكل صحي"))
        assertTrue(SearchMatcher.matches("إدارة", "ادارة المصروفات"))
        assertTrue(SearchMatcher.matches("على", "علي بابا"))
    }

    @Test fun partOfAWordStillMatchesLikeBefore() {
        assertTrue(SearchMatcher.matches("طبخ", "مطبخ الأسرة"))
    }

    @Test fun everyMeaningfulWordMustBePresent() {
        assertTrue(SearchMatcher.matches("طبخ دجاج", "طبخ الدجاج بالفرن"))
        assertFalse(SearchMatcher.matches("طبخ دجاج", "طبخ الأرز"))
    }

    @Test fun commonWordsInTheQueryAreIgnored() {
        assertTrue(SearchMatcher.matches("في طبخ", "طبخ الأرز"))
        assertTrue(SearchMatcher.matches("في", "في الصباح"))
    }

    @Test fun anyFieldCanSatisfyTheQueryAndNullFieldsAreSafe() {
        assertTrue(SearchMatcher.matches("طبخ كبسة", "كبسة", null, "طبخ"))
        assertFalse(SearchMatcher.matches("طبخ", "حاسوب", null))
    }

    @Test fun blankQueryMatchesEverythingAndLatinIsCaseInsensitive() {
        assertTrue(SearchMatcher.matches("  ", "أي شيء"))
        assertTrue(SearchMatcher.matches("Kotlin", "kotlin tips"))
        assertFalse(SearchMatcher.matches("زحل", "المريخ"))
    }
}
