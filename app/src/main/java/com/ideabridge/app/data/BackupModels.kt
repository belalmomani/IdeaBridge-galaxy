package com.ideabridge.app.data

/** محتوى نسخة احتياطية بعد قراءتها والتحقق منها (بمعرفات الملف، لا معرفات قاعدة البيانات). */
data class ParsedGalaxy(val id: Long, val name: String, val colorIndex: Int, val createdAt: Long)
data class ParsedNote(
    val id: Long, val galaxyId: Long, val title: String, val body: String,
    val createdAt: Long, val updatedAt: Long,
)
data class ParsedRelation(val firstNoteId: Long, val secondNoteId: Long, val type: RelationType)
data class ParsedBackup(
    val galaxies: List<ParsedGalaxy>,
    val notes: List<ParsedNote>,
    val relations: List<ParsedRelation>,
)

data class ImportReport(
    val galaxiesAdded: Int,
    val notesAdded: Int,
    val notesSkipped: Int,
    val relationsAdded: Int,
)

sealed interface ImportOutcome {
    data class Done(val report: ImportReport) : ImportOutcome
    data object Invalid : ImportOutcome
    data object Failed : ImportOutcome
}
