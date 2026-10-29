package com.ideabridge.app

import com.ideabridge.app.engine.ListEditing

/** أخطاء التحقق والحفظ بصيغة مستقلة عن الواجهة. */
enum class IdeaError {
    EmptyTitle, TitleTooLong, DescriptionTooLong,
    SameIdea, IdeaNotFound, RelationNotFound, DuplicateRelation,
    EmptyGalaxyName, GalaxyNameTooLong, GalaxyNotFound, Unknown
}

/** استثناء داخلي لإلغاء معاملة قاعدة البيانات بخطأ معروف. */
class IdeaException(val error: IdeaError) : Exception(error.name)

sealed interface SaveResult<out T> {
    data class Success<T>(val value: T) : SaveResult<T>
    data class Failure(val error: IdeaError) : SaveResult<Nothing>
}

/** منطق تحقق بحت (بدون Android) لسهولة الاختبار على JVM. */
object IdeaRules {
    const val MAX_LENGTH = 140          // أقصى طول للعنوان
    const val MAX_DESCRIPTION = 20000  // أقصى طول لنص الملاحظة
    const val MAX_GALAXY_NAME = 40

    fun cleanTitle(raw: String): String = raw.trim()
    fun cleanDescription(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    fun validateIdea(title: String, description: String?): IdeaError? = when {
        cleanTitle(title).isEmpty() -> IdeaError.EmptyTitle
        cleanTitle(title).length > MAX_LENGTH -> IdeaError.TitleTooLong
        (cleanDescription(description)?.length ?: 0) > MAX_DESCRIPTION -> IdeaError.DescriptionTooLong
        else -> null
    }

    /** عنوان الملاحظة: العنوان المكتوب، وإن كان فارغًا فأول سطر غير فارغ من النص (كما في ملاحظات الهاتف). */
    fun effectiveTitle(title: String, body: String): String {
        val t = cleanTitle(title)
        if (t.isNotEmpty()) return t.take(MAX_LENGTH)
        val firstLine = body.lineSequence()
            .map { ListEditing.stripMarker(it.trim()).trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return firstLine.take(MAX_LENGTH)
    }

    fun validateGalaxyName(raw: String): IdeaError? = when {
        raw.trim().isEmpty() -> IdeaError.EmptyGalaxyName
        raw.trim().length > MAX_GALAXY_NAME -> IdeaError.GalaxyNameTooLong
        else -> null
    }

    fun validateRelation(firstId: Long, secondId: Long): IdeaError? =
        if (firstId == secondId) IdeaError.SameIdea else null

    /** يهرّب محارف LIKE الخاصة حتى يُبحث عن النص حرفيًا. */
    fun likePattern(query: String): String =
        query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
