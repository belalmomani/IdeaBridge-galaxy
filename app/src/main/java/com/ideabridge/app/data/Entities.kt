package com.ideabridge.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class RelationType { SIMILARITY, CAUSE, EFFECT, DEPENDENCY, MERGE }

class Converters {
    @TypeConverter fun fromType(type: RelationType): String = type.name
    @TypeConverter fun toType(value: String): RelationType = RelationType.valueOf(value)
}

/** المجرة: دفتر يضم ملاحظات. */
@Entity(tableName = "galaxies")
data class GalaxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
)

/** صف عرض: مجرة مع عدد ملاحظاتها. */
data class GalaxyRow(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
    val noteCount: Int,
)

/**
 * الملاحظة (اسم الجدول بقي "ideas" للحفاظ على بيانات النسخة السابقة).
 * title = العنوان، description = نص الملاحظة. galaxyId لا يملك قيد مفتاح أجنبي (يُدار في المستودع).
 */
@Entity(tableName = "ideas", indices = [Index("galaxyId")])
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val galaxyId: Long = 0,
)

/** علاقة موجّهة: الملاحظة الأولى ← (النوع) ← الملاحظة الثانية. */
@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(
            entity = IdeaEntity::class,
            parentColumns = ["id"], childColumns = ["firstIdeaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IdeaEntity::class,
            parentColumns = ["id"], childColumns = ["secondIdeaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("firstIdeaId"),
        Index("secondIdeaId"),
        Index(value = ["firstIdeaId", "secondIdeaId", "type"], unique = true),
    ],
)
data class RelationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val firstIdeaId: Long,
    val secondIdeaId: Long,
    val type: RelationType,
    val createdAt: Long,
)

/** صف عرض: علاقة مع عنوانَي طرفيها. */
data class RelationRow(
    val id: Long,
    val firstIdeaId: Long,
    val secondIdeaId: Long,
    val type: RelationType,
    val createdAt: Long,
    @ColumnInfo(name = "firstTitle") val firstTitle: String,
    @ColumnInfo(name = "secondTitle") val secondTitle: String,
)
