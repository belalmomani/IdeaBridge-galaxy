package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListEditingTest {
    @Test fun checklistIsAddedToTheCurrentLineAndRemovedOnSecondToggle() {
        val added = ListEditing.toggleChecklist("شراء حليب", 3, 3)
        assertEquals("☐ شراء حليب", added.text)
        assertEquals(added.text.length, added.selEnd)
        assertEquals("شراء حليب", ListEditing.toggleChecklist(added.text, 3, 3).text)
    }

    @Test fun checklistAppliesToEverySelectedLineAndSkipsBlankOnes() {
        val t = "أ\nب\n\nج"
        assertEquals("☐ أ\n☐ ب\n\n☐ ج", ListEditing.toggleChecklist(t, 0, t.length).text)
        assertEquals("أ\nب", ListEditing.toggleChecklist("☐ أ\n☑ ب", 0, 7).text)
    }

    @Test fun bulletConvertsAChecklistLineInsteadOfStacking() {
        assertEquals("• أ", ListEditing.toggleBullet("☐ أ", 0, 0).text)
        assertEquals("☐ ", ListEditing.toggleChecklist("", 0, 0).text)
    }

    @Test fun doneTogglesOnlyTaskLines() {
        assertEquals("☑ أ", ListEditing.toggleDone("☐ أ", 3).text)
        assertEquals("☐ أ", ListEditing.toggleDone("☑ أ", 3).text)
        assertEquals("أ", ListEditing.toggleDone("أ", 1).text)
        assertEquals("س\n☑ أ", ListEditing.toggleDone("س\n☐ أ", 5).text)
    }

    @Test fun enterContinuesAListWithTheRightMarker() {
        assertEquals(ListEditing.Edit("☐ أ\n☐ ", 6, 6), ListEditing.continueList("☐ أ", "☐ أ\n", 4))
        assertEquals(ListEditing.Edit("☑ أ\n☐ ", 6, 6), ListEditing.continueList("☑ أ", "☑ أ\n", 4))
        assertEquals(ListEditing.Edit("• أ\n• ", 6, 6), ListEditing.continueList("• أ", "• أ\n", 4))
        assertEquals(ListEditing.Edit("☐ a\n☐ b", 6, 6), ListEditing.continueList("☐ ab", "☐ a\nb", 4))
    }

    @Test fun enterOnAnEmptyItemEndsTheList() {
        assertEquals(ListEditing.Edit("☐ أ\n", 4, 4), ListEditing.continueList("☐ أ\n☐ ", "☐ أ\n☐ \n", 7))
    }

    @Test fun ordinaryTypingIsNeverTouched() {
        assertNull(ListEditing.continueList("أ", "أ\n", 2))
        assertNull(ListEditing.continueList("☐ أ", "☐ أب", 4))
        assertNull(ListEditing.continueList("☐ أ", "☐ ", 2))
    }

    @Test fun markersDoNotBecomeTheNoteTitle() {
        assertEquals("شراء حليب", IdeaRules.effectiveTitle("", "☐ شراء حليب\n☐ خبز"))
        assertEquals("خبز", IdeaRules.effectiveTitle("", "☐ \n• خبز"))
        assertEquals("عنواني", IdeaRules.effectiveTitle("عنواني", "☐ شراء"))
    }
}
