package com.ideabridge.app.engine

import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.RelationEntity

/** يبني نسخة احتياطية JSON بلا أي مكتبة خارجية (ويعمل في اختبارات JVM العادية). */
object BackupJson {
    const val FORMAT_VERSION = 1

    fun toJson(
        galaxies: List<GalaxyEntity>,
        notes: List<IdeaEntity>,
        relations: List<RelationEntity>,
        exportedAt: Long,
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": \"IdeaBridge\",\n")
        sb.append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(exportedAt).append(",\n")
        sb.append("  \"galaxies\": [")
        sb.append(galaxies.joinToString(",") {
            "\n    {\"id\": ${it.id}, \"name\": ${quote(it.name)}, \"colorIndex\": ${it.colorIndex}, \"createdAt\": ${it.createdAt}}"
        })
        sb.append("\n  ],\n  \"notes\": [")
        sb.append(notes.joinToString(",") {
            "\n    {\"id\": ${it.id}, \"galaxyId\": ${it.galaxyId}, \"title\": ${quote(it.title)}, " +
                "\"body\": ${quote(it.description.orEmpty())}, \"createdAt\": ${it.createdAt}, \"updatedAt\": ${it.updatedAt}}"
        })
        sb.append("\n  ],\n  \"relations\": [")
        sb.append(relations.joinToString(",") {
            "\n    {\"id\": ${it.id}, \"firstNoteId\": ${it.firstIdeaId}, \"secondNoteId\": ${it.secondIdeaId}, " +
                "\"type\": ${quote(it.type.name)}, \"createdAt\": ${it.createdAt}}"
        })
        sb.append("\n  ]\n}\n")
        return sb.toString()
    }

    fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch.code < 0x20 -> sb.append("\\u").append(String.format("%04x", ch.code))
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
