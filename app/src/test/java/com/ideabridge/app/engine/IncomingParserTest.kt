package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingParserTest {
    @Test fun sharedPlainTextBecomesASharedNoteWithTrimmedTitleAndBody() {
        val r = IncomingParser.parse(IncomingParser.ACTION_SEND, "text/plain", "  عنوان  ", "  نص مُشارَك \n")
        assertEquals(Incoming.SharedText("عنوان", "نص مُشارَك"), r)
    }

    @Test fun aMissingSubjectGivesAnEmptyTitleSoTheFirstLineIsUsedLater() {
        assertEquals(Incoming.SharedText("", "رابط https://example.com"), IncomingParser.parse(IncomingParser.ACTION_SEND, "text/plain", null, "رابط https://example.com"))
    }

    @Test fun theShortcutActionOpensANewNote() {
        assertEquals(Incoming.NewNote, IncomingParser.parse(IncomingParser.ACTION_NEW_NOTE, null, null, null))
    }

    @Test fun emptyOrNonTextShareIsIgnored() {
        assertNull(IncomingParser.parse(IncomingParser.ACTION_SEND, "text/plain", "عنوان", "   "))
        assertNull(IncomingParser.parse(IncomingParser.ACTION_SEND, "text/plain", "عنوان", null))
        assertNull(IncomingParser.parse(IncomingParser.ACTION_SEND, "image/png", null, "نص"))
        assertNull(IncomingParser.parse(IncomingParser.ACTION_SEND, null, null, "نص"))
    }

    @Test fun unknownActionsAreIgnored() {
        assertNull(IncomingParser.parse("android.intent.action.MAIN", null, null, null))
        assertNull(IncomingParser.parse(null, null, null, null))
    }

    @Test fun veryLongSharedTextIsCappedToTheNoteLimits() {
        val r = IncomingParser.parse(IncomingParser.ACTION_SEND, "text/plain", "ا".repeat(500), "ب".repeat(30_000)) as Incoming.SharedText
        assertEquals(IdeaRules.MAX_LENGTH, r.title.length)
        assertEquals(IdeaRules.MAX_DESCRIPTION, r.body.length)
    }
}
