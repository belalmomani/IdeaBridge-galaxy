package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules
import com.ideabridge.app.data.ParsedBackup
import com.ideabridge.app.data.ParsedGalaxy
import com.ideabridge.app.data.ParsedNote
import com.ideabridge.app.data.ParsedRelation
import com.ideabridge.app.data.RelationType
import org.json.JSONArray
import org.json.JSONObject

/** يقرأ نسخة احتياطية JSON أنتجها BackupJson ويتحقق منها دون أن يرمي استثناءً للمستدعي. */
object BackupParser {
    const val MAX_BYTES = 20 * 1024 * 1024

    sealed interface Result {
        data class Ok(val backup: ParsedBackup) : Result
        data class Invalid(val reason: String) : Result
    }

    fun parse(json: String): Result {
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "IdeaBridge") return Result.Invalid("not an IdeaBridge backup")
            if (root.optInt("formatVersion", -1) != BackupJson.FORMAT_VERSION) return Result.Invalid("unsupported version")

            val galaxies = ArrayList<ParsedGalaxy>()
            val galaxyArray = root.optJSONArray("galaxies") ?: JSONArray()
            for (i in 0 until galaxyArray.length()) {
                val o = galaxyArray.getJSONObject(i)
                val name = o.getString("name").trim().take(IdeaRules.MAX_GALAXY_NAME)
                if (name.isEmpty()) continue
                galaxies.add(ParsedGalaxy(o.getLong("id"), name, o.optInt("colorIndex", 0), o.optLong("createdAt", 0L)))
            }

            val notes = ArrayList<ParsedNote>()
            val noteArray = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until noteArray.length()) {
                val o = noteArray.getJSONObject(i)
                val body = o.optString("body", "").take(IdeaRules.MAX_DESCRIPTION)
                val title = IdeaRules.effectiveTitle(o.optString("title", ""), body)
                if (title.isEmpty()) continue // لا ملاحظات فارغة تمامًا
                notes.add(
                    ParsedNote(
                        id = o.getLong("id"), galaxyId = o.optLong("galaxyId", 0L), title = title, body = body,
                        createdAt = o.optLong("createdAt", 0L), updatedAt = o.optLong("updatedAt", 0L),
                    )
                )
            }

            val relations = ArrayList<ParsedRelation>()
            val relationArray = root.optJSONArray("relations") ?: JSONArray()
            for (i in 0 until relationArray.length()) {
                val o = relationArray.getJSONObject(i)
                val type = try {
                    RelationType.valueOf(o.getString("type"))
                } catch (e: IllegalArgumentException) {
                    continue // نوع غير معروف: يُتجاهل الرابط
                }
                relations.add(ParsedRelation(o.getLong("firstNoteId"), o.getLong("secondNoteId"), type))
            }
            Result.Ok(ParsedBackup(galaxies, notes, relations))
        } catch (e: Exception) {
            Result.Invalid(e.message ?: "invalid json")
        }
    }
}
