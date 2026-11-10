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

    @Test fun numberingAssignsSequentialNumbersAndSkipsBlankLines() {
        assertEquals("1. حليب\n2. خبز\n3. بيض", ListEditing.toggleNumbered("حليب\nخبز\nبيض", 0, 11).text)
        assertEquals("1. أ\n\n2. ب", ListEditing.toggleNumbered("أ\n\nب", 0, 4).text)
        assertEquals("1. ", ListEditing.toggleNumbered("", 0, 0).text)
    }

    @Test fun numberingTogglesOffAndConvertsOtherListTypes() {
        assertEquals("حليب\nخبز", ListEditing.toggleNumbered("1. حليب\n2. خبز", 0, 13).text)
        assertEquals("1. أ\n2. ب", ListEditing.toggleNumbered("☐ أ\n• ب", 0, 6).text)
        assertEquals("1. أ\n2. ب", ListEditing.toggleNumbered("1. أ\nب", 0, 6).text) // مختلط: يُعاد ترقيم الكل
    }

    @Test fun enterContinuesANumberedListWithTheNextNumber() {
        assertEquals(ListEditing.Edit("1. أ\n2. ", 8, 8), ListEditing.continueList("1. أ", "1. أ\n", 5))
        assertEquals(ListEditing.Edit("9. أ\n10. ", 9, 9), ListEditing.continueList("9. أ", "9. أ\n", 5))
        assertEquals(ListEditing.Edit("5. a\n6. b", 8, 8), ListEditing.continueList("5. ab", "5. a\nb", 5))
    }

    @Test fun enterOnAnEmptyNumberedItemEndsTheListAndNonListTextIsUntouched() {
        assertEquals(ListEditing.Edit("1. أ\n", 5, 5), ListEditing.continueList("1. أ\n2. ", "1. أ\n2. \n", 9))
        assertNull(ListEditing.continueList("نص", "نص\n", 3))
        assertNull(ListEditing.continueList("1.أ", "1.أ\n", 4)) // بلا مسافة بعد النقطة: ليس ترقيمًا
    }

    @Test fun numberedMarkersDoNotBecomeTheNoteTitle() {
        assertEquals("شراء حليب", IdeaRules.effectiveTitle("", "1. شراء حليب\n2. خبز"))
        assertEquals("نص 1. وسط", IdeaRules.effectiveTitle("", "نص 1. وسط"))
    }
}
