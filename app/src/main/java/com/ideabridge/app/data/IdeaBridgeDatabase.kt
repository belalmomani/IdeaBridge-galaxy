package com.ideabridge.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GalaxyEntity::class, IdeaEntity::class, RelationEntity::class, RejectedLinkEntity::class, TrashEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IdeaBridgeDatabase : RoomDatabase() {
    abstract fun dao(): IdeaDao

    companion object {
        const val FILE_NAME = "ideabridge.db"
        const val DEFAULT_GALAXY_NAME = "ملاحظاتي"
        @Volatile private var instance: IdeaBridgeDatabase? = null

        /** 1 → 2: إضافة المجرات، وإسناد كل الملاحظات الموجودة إلى مجرة «ملاحظاتي» دون حذف أي شيء. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `galaxies` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `ideas` ADD COLUMN `galaxyId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "INSERT INTO `galaxies` (`id`, `name`, `colorIndex`, `createdAt`) " +
                        "VALUES (1, '$DEFAULT_GALAXY_NAME', 0, ${System.currentTimeMillis()})"
                )
                db.execSQL("UPDATE `ideas` SET `galaxyId` = 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ideas_galaxyId` ON `ideas` (`galaxyId`)")
            }
        }

        /** 2 → 3: جدول الاقتراحات المرفوضة. لا يمس أي جدول موجود. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rejected_links` (`pairKey` TEXT NOT NULL, " +
                        "`rejectedAt` INTEGER NOT NULL, PRIMARY KEY(`pairKey`))"
                )
            }
        }

        /** 3 → 4: عمود تثبيت الملاحظة؛ كل الملاحظات الحالية غير مثبّتة. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ideas` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 4 → 5: جدول سلة المحذوفات. لا يمس أي جدول موجود. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trash` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `noteCount` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, `payload` TEXT NOT NULL)"
                )
            }
        }

        // لا نستخدم fallbackToDestructiveMigration عمدًا حتى لا تُحذف بيانات المستخدم بصمت.
        fun get(context: Context): IdeaBridgeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, IdeaBridgeDatabase::class.java, FILE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
