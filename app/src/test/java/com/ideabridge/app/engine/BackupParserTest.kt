package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules
import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.data.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupParserTest {
    private fun ok(json: String) = (BackupParser.parse(json) as BackupParser.Result.Ok).backup

    @Test fun exportThenParseKeepsEverything() {
        val json = BackupJson.toJson(
            galaxies = listOf(GalaxyEntity(1, "ملاحظاتي", 0, 10L), GalaxyEntity(2, "طبخ", 3, 20L)),
            notes = listOf(
                IdeaEntity(1, "عنوان", null, 11L, 12L, 1),
                IdeaEntity(2, "فيها \"اقتباس\"", "سطر1\nسطر2", 13L, 14L, 2),
            ),
            relations = listOf(RelationEntity(1, 1, 2, RelationType.CAUSE, 15L)),
            exportedAt = 99L,
        )
        val b = ok(json)
        assertEquals(listOf("ملاحظاتي", "طبخ"), b.galaxies.map { it.name })
        assertEquals(3, b.galaxies[1].colorIndex)
        assertEquals(listOf("عنوان", "فيها \"اقتباس\""), b.notes.map { it.title })
        assertEquals("سطر1\nسطر2", b.notes[1].body)
        assertEquals(2L, b.notes[1].galaxyId)
        assertEquals(13L, b.notes[1].createdAt)
        assertEquals(1, b.relations.size)
        assertEquals(RelationType.CAUSE, b.relations[0].type)
    }

    @Test fun malformedOrForeignFilesAreRejectedWithoutCrashing() {
        assertTrue(BackupParser.parse("not json at all") is BackupParser.Result.Invalid)
        assertTrue(BackupParser.parse("") is BackupParser.Result.Invalid)
        assertTrue(BackupParser.parse("{}") is BackupParser.Result.Invalid)
        assertTrue(BackupParser.parse("{\"app\":\"Other\",\"formatVersion\":1}") is BackupParser.Result.Invalid)
        assertTrue(BackupParser.parse("{\"app\":\"IdeaBridge\",\"formatVersion\":99}") is BackupParser.Result.Invalid)
    }

    @Test fun missingListsAreTreatedAsEmpty() {
        val b = ok("{\"app\":\"IdeaBridge\",\"formatVersion\":1}")
        assertTrue(b.galaxies.isEmpty() && b.notes.isEmpty() && b.relations.isEmpty())
    }

    @Test fun titleFallsBackToBodyAndEmptyNotesAreDropped() {
        val json = """{"app":"IdeaBridge","formatVersion":1,"notes":[
            {"id":1,"galaxyId":1,"title":"","body":"أول سطر\nثان","createdAt":1,"updatedAt":1},
            {"id":2,"galaxyId":1,"title":"  ","body":"  ","createdAt":1,"updatedAt":1}]}"""
        val b = ok(json)
        assertEquals(1, b.notes.size)
        assertEquals("أول سطر", b.notes[0].title)
    }

    @Test fun oversizedTextIsTruncatedToTheLimits() {
        val longTitle = "ا".repeat(IdeaRules.MAX_LENGTH + 50)
        val longBody = "ب".repeat(IdeaRules.MAX_DESCRIPTION + 50)
        val json = """{"app":"IdeaBridge","formatVersion":1,"galaxies":[{"id":1,"name":"${"ج".repeat(80)}","colorIndex":0,"createdAt":1}],
            "notes":[{"id":1,"galaxyId":1,"title":"$longTitle","body":"$longBody","createdAt":1,"updatedAt":1}]}"""
        val b = ok(json)
        assertEquals(IdeaRules.MAX_LENGTH, b.notes[0].title.length)
        assertEquals(IdeaRules.MAX_DESCRIPTION, b.notes[0].body.length)
        assertEquals(IdeaRules.MAX_GALAXY_NAME, b.galaxies[0].name.length)
    }

    @Test fun unknownRelationTypesAreSkippedNotFatal() {
        val json = """{"app":"IdeaBridge","formatVersion":1,"relations":[
            {"id":1,"firstNoteId":1,"secondNoteId":2,"type":"CAUSE","createdAt":1},
            {"id":2,"firstNoteId":1,"secondNoteId":2,"type":"FUTURE_TYPE","createdAt":1}]}"""
        assertEquals(listOf(RelationType.CAUSE), ok(json).relations.map { it.type })
    }
}
