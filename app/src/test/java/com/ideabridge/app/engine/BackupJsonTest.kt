package com.ideabridge.app.engine

import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.data.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    @Test fun quoteEscapesSpecialCharactersAndKeepsArabic() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", BackupJson.quote("a\"b\\c\nd"))
        assertEquals("\"\\u0001\"", BackupJson.quote("\u0001"))
        assertEquals("\"ملاحظة\"", BackupJson.quote("ملاحظة"))
    }

    @Test fun exportContainsAllDataWithoutLosingText() {
        val json = BackupJson.toJson(
            galaxies = listOf(GalaxyEntity(1, "ملاحظاتي", 0, 10L)),
            notes = listOf(
                IdeaEntity(1, "عنوان", null, 11L, 12L, 1),
                IdeaEntity(2, "فيها \"اقتباس\"", "سطر1\nسطر2", 13L, 14L, 1),
            ),
            relations = listOf(RelationEntity(1, 1, 2, RelationType.CAUSE, 15L)),
            exportedAt = 99L,
        )
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\": 99"))
        assertTrue(json.contains("\"name\": \"ملاحظاتي\""))
        assertTrue(json.contains("\"title\": \"عنوان\", \"body\": \"\""))
        assertTrue(json.contains("فيها \\\"اقتباس\\\""))
        assertTrue(json.contains("سطر1\\nسطر2"))
        assertTrue(json.contains("\"type\": \"CAUSE\""))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test fun emptyDataStillProducesStructure() {
        val json = BackupJson.toJson(emptyList(), emptyList(), emptyList(), 1L)
        assertTrue(json.contains("\"galaxies\": ["))
        assertTrue(json.contains("\"notes\": ["))
        assertTrue(json.contains("\"relations\": ["))
    }
}
