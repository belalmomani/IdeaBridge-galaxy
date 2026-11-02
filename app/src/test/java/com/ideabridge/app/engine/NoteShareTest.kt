package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteShareTest {
    @Test fun titleAndBodyAreSeparatedByABlankLine() {
        assertEquals("عنوان\n\nنص الملاحظة", NoteShare.format("عنوان", "نص الملاحظة"))
    }

    @Test fun aBlankTitleSharesOnlyTheBody() {
        assertEquals("سطر أول\nسطر ثان", NoteShare.format("", "سطر أول\nسطر ثان"))
    }

    @Test fun theTitleIsNotRepeatedWhenItIsTheFirstLineOfTheBody() {
        assertEquals("شراء حليب\nخبز", NoteShare.format("شراء حليب", "شراء حليب\nخبز"))
    }

    @Test fun listMarkersAndWhitespaceAreHandled() {
        assertEquals("مهام\n\n☐ أ\n☑ ب", NoteShare.format("  مهام ", "\n☐ أ\n☑ ب  \n"))
    }

    @Test fun anEmptyNoteSharesNothing() {
        assertEquals("", NoteShare.format("  ", " \n "))
        assertEquals("عنوان فقط", NoteShare.format("عنوان فقط", ""))
    }
}
