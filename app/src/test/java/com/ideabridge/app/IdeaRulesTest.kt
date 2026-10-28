package com.ideabridge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdeaRulesTest {
    @Test fun emptyTitleIsRejected() {
        assertEquals(IdeaError.EmptyTitle, IdeaRules.validateIdea("", null))
    }
    @Test fun blankTitleIsRejected() {
        assertEquals(IdeaError.EmptyTitle, IdeaRules.validateIdea("  \n ", "وصف"))
    }
    @Test fun validTitleAccepted() {
        assertNull(IdeaRules.validateIdea("تربية الدجاج", null))
    }
    @Test fun titleIsTrimmed() {
        assertEquals("تربية الدجاج", IdeaRules.cleanTitle("  تربية الدجاج "))
    }
    @Test fun longTitleIsRejected() {
        assertEquals(IdeaError.TitleTooLong, IdeaRules.validateIdea("ا".repeat(IdeaRules.MAX_LENGTH + 1), null))
    }
    @Test fun longDescriptionIsRejected() {
        assertEquals(
            IdeaError.DescriptionTooLong,
            IdeaRules.validateIdea("عنوان", "ا".repeat(IdeaRules.MAX_DESCRIPTION + 1)),
        )
    }
    @Test fun blankDescriptionBecomesNull() {
        assertNull(IdeaRules.cleanDescription("   "))
        assertNull(IdeaRules.cleanDescription(null))
    }
    @Test fun relationToSelfIsRejected() {
        assertEquals(IdeaError.SameIdea, IdeaRules.validateRelation(3, 3))
        assertNull(IdeaRules.validateRelation(3, 4))
    }
    @Test fun likePatternEscapesWildcards() {
        assertEquals("100\\%\\_", IdeaRules.likePattern(" 100%_ "))
        assertEquals("a\\\\b", IdeaRules.likePattern("a\\b"))
    }

    @Test fun effectiveTitleUsesTypedTitleFirst() {
        assertEquals("عنواني", IdeaRules.effectiveTitle("  عنواني ", "سطر أول\nسطر ثان"))
    }
    @Test fun effectiveTitleFallsBackToFirstNonEmptyLine() {
        assertEquals("قائمة المشتريات", IdeaRules.effectiveTitle("", "\n  قائمة المشتريات  \nحليب"))
    }
    @Test fun effectiveTitleIsEmptyWhenNothingWritten() {
        assertEquals("", IdeaRules.effectiveTitle("   ", " \n \n"))
    }
    @Test fun effectiveTitleIsCappedToMaxLength() {
        assertEquals(IdeaRules.MAX_LENGTH, IdeaRules.effectiveTitle("", "ا".repeat(500)).length)
    }
    @Test fun galaxyNameValidation() {
        assertEquals(IdeaError.EmptyGalaxyName, IdeaRules.validateGalaxyName("   "))
        assertEquals(IdeaError.GalaxyNameTooLong, IdeaRules.validateGalaxyName("ا".repeat(IdeaRules.MAX_GALAXY_NAME + 1)))
        assertNull(IdeaRules.validateGalaxyName("مشاريعي"))
    }
}
