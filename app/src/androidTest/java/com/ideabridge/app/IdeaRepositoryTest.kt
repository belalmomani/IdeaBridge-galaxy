package com.ideabridge.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ideabridge.app.data.DeletedSnapshot
import com.ideabridge.app.data.IdeaBridgeDatabase
import com.ideabridge.app.data.IdeaRepository
import com.ideabridge.app.data.RelationType
import com.ideabridge.app.engine.BackupJson
import com.ideabridge.app.engine.BackupParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdeaRepositoryTest {
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository
    private var now = 1_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db) { now++ }
    }

    @After fun tearDown() { db.close() }

    private fun add(title: String, description: String? = null): Long =
        (runBlocking { repo.addIdea(title, description) } as SaveResult.Success).value

    private fun failure(r: SaveResult<*>): IdeaError = (r as SaveResult.Failure).error

    @Test fun addThenReadBack() = runBlocking {
        val id = add("تربية الدجاج", "وصف")
        val saved = db.dao().getById(id)!!
        assertEquals("تربية الدجاج", saved.title)
        assertEquals("وصف", saved.description)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test fun emptyIdeaIsNotSaved() = runBlocking {
        assertEquals(IdeaError.EmptyTitle, failure(repo.addIdea("   ", null)))
        assertTrue(db.dao().getAllIdeas().isEmpty())
    }

    @Test fun updateKeepsIdAndDoesNotDuplicate() = runBlocking {
        val id = add("قديم")
        val before = db.dao().getById(id)!!
        assertTrue(repo.updateIdea(id, "جديد", "وصف") is SaveResult.Success)
        val after = db.dao().getById(id)!!
        assertEquals(id, after.id)
        assertEquals("جديد", after.title)
        assertEquals(before.createdAt, after.createdAt)
        assertNotEquals(before.updatedAt, after.updatedAt)
        assertEquals(1, db.dao().getAllIdeas().size)
    }

    @Test fun updateMissingIdeaFails() = runBlocking {
        assertEquals(IdeaError.IdeaNotFound, failure(repo.updateIdea(999, "x", null)))
    }

    @Test fun deleteIdeaRemovesItsRelations() = runBlocking {
        val a = add("أ"); val b = add("ب"); val c = add("ج")
        repo.addRelation(a, b, RelationType.CAUSE)
        repo.addRelation(c, a, RelationType.DEPENDENCY)
        repo.addRelation(b, c, RelationType.SIMILARITY)
        assertTrue(repo.deleteIdea(a) is SaveResult.Success)
        val left = db.dao().getAllRelations()
        assertEquals(1, left.size)
        assertEquals(b, left[0].firstIdeaId)
        assertEquals(c, left[0].secondIdeaId)
    }

    @Test fun validRelationIsCreated() = runBlocking {
        val a = add("أ"); val b = add("ب")
        assertTrue(repo.addRelation(a, b, RelationType.CAUSE) is SaveResult.Success)
        val rows = repo.observeRelations(a).first()
        assertEquals(1, rows.size)
        assertEquals("أ", rows[0].firstTitle)
        assertEquals("ب", rows[0].secondTitle)
    }

    @Test fun selfRelationIsRejected() = runBlocking {
        val a = add("أ")
        assertEquals(IdeaError.SameIdea, failure(repo.addRelation(a, a, RelationType.MERGE)))
        assertTrue(db.dao().getAllRelations().isEmpty())
    }

    @Test fun relationWithMissingIdeaIsRejected() = runBlocking {
        val a = add("أ")
        assertEquals(IdeaError.IdeaNotFound, failure(repo.addRelation(a, 12345, RelationType.CAUSE)))
        assertEquals(IdeaError.IdeaNotFound, failure(repo.addRelation(12345, a, RelationType.CAUSE)))
        assertTrue(db.dao().getAllRelations().isEmpty())
    }

    @Test fun duplicateRelationRejectedButReverseAndOtherTypeAllowed() = runBlocking {
        val a = add("أ"); val b = add("ب")
        assertTrue(repo.addRelation(a, b, RelationType.CAUSE) is SaveResult.Success)
        assertEquals(IdeaError.DuplicateRelation, failure(repo.addRelation(a, b, RelationType.CAUSE)))
        assertTrue(repo.addRelation(b, a, RelationType.CAUSE) is SaveResult.Success)
        assertTrue(repo.addRelation(a, b, RelationType.EFFECT) is SaveResult.Success)
        assertEquals(3, db.dao().getAllRelations().size)
    }

    @Test fun deleteRelationRemovesOnlyThatRelation() = runBlocking {
        val a = add("أ"); val b = add("ب")
        val r1 = (repo.addRelation(a, b, RelationType.CAUSE) as SaveResult.Success).value
        repo.addRelation(a, b, RelationType.EFFECT)
        assertTrue(repo.deleteRelation(r1) is SaveResult.Success)
        assertEquals(1, db.dao().getAllRelations().size)
        assertEquals(IdeaError.RelationNotFound, failure(repo.deleteRelation(r1)))
    }

    @Test fun searchFindsTitleAndDescriptionAndEscapesWildcards() = runBlocking {
        add("تربية الدجاج"); add("إدارة المصروفات", "تسجيل الأعلاف"); add("خصم 50%")
        assertEquals(listOf("تربية الدجاج"), repo.observeIdeas("الدجاج").first().map { it.title })
        assertEquals(listOf("إدارة المصروفات"), repo.observeIdeas("الأعلاف").first().map { it.title })
        assertEquals(listOf("خصم 50%"), repo.observeIdeas("%").first().map { it.title })
        assertEquals(3, repo.observeIdeas("").first().size)
        assertTrue(repo.observeIdeas("لا يوجد").first().isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class GalaxyRepositoryTest {
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun id(r: SaveResult<Long>): Long = (r as SaveResult.Success).value

    @Test fun defaultGalaxyIsCreatedOnceAndUsedForFirstNote() = runBlocking {
        val g1 = id(repo.ensureDefaultGalaxy())
        val g2 = id(repo.ensureDefaultGalaxy())
        assertEquals(g1, g2)
        val note = id(repo.addIdea("أول ملاحظة", "نص"))
        assertEquals(g1, db.dao().getById(note)!!.galaxyId)
        assertEquals(1, db.dao().getAllGalaxies().size)
    }

    @Test fun noteIsStoredInChosenGalaxyAndFoundBySearch() = runBlocking {
        val work = id(repo.createGalaxy("العمل", 2))
        val home = id(repo.createGalaxy("البيت", 3))
        id(repo.addIdea("اجتماع الأحد", "جدول الأعمال", work))
        id(repo.addIdea("قائمة السوق", "خبز وحليب", home))
        assertEquals(listOf("اجتماع الأحد"), repo.observeIdeasInGalaxy(work, "").first().map { it.title })
        assertEquals(listOf("قائمة السوق"), repo.observeIdeasInGalaxy(home, "حليب").first().map { it.title })
        assertTrue(repo.observeIdeasInGalaxy(work, "حليب").first().isEmpty())
        val rows = repo.observeGalaxies().first().associateBy { it.name }
        assertEquals(1, rows.getValue("العمل").noteCount)
    }

    @Test fun noteInMissingGalaxyIsRejected() = runBlocking {
        assertEquals(IdeaError.GalaxyNotFound, (repo.addIdea("x", null, 999) as SaveResult.Failure).error)
        assertTrue(db.dao().getAllIdeas().isEmpty())
    }

    @Test fun invalidGalaxyNamesAreRejected() = runBlocking {
        assertEquals(IdeaError.EmptyGalaxyName, (repo.createGalaxy("  ", 0) as SaveResult.Failure).error)
        assertTrue(db.dao().getAllGalaxies().isEmpty())
    }

    @Test fun renameKeepsNotes() = runBlocking {
        val g = id(repo.createGalaxy("قديم", 0))
        id(repo.addIdea("ملاحظة", null, g))
        assertTrue(repo.updateGalaxy(g, "جديد", 4) is SaveResult.Success)
        assertEquals("جديد", db.dao().getGalaxy(g)!!.name)
        assertEquals(1, repo.observeIdeasInGalaxy(g, "").first().size)
    }

    @Test fun deletingGalaxyRemovesItsNotesAndTheirRelationsOnly() = runBlocking {
        val a = id(repo.createGalaxy("أ", 0))
        val b = id(repo.createGalaxy("ب", 1))
        val n1 = id(repo.addIdea("في أ", null, a))
        val n2 = id(repo.addIdea("في أ أيضًا", null, a))
        val keep1 = id(repo.addIdea("في ب 1", null, b))
        val keep2 = id(repo.addIdea("في ب 2", null, b))
        repo.addRelation(n1, n2, RelationType.SIMILARITY)
        repo.addRelation(n1, keep1, RelationType.CAUSE)
        repo.addRelation(keep1, keep2, RelationType.DEPENDENCY)
        assertTrue(repo.deleteGalaxy(a) is SaveResult.Success)
        assertNull(db.dao().getGalaxy(a))
        assertEquals(listOf("في ب 1", "في ب 2"), db.dao().getAllIdeas().map { it.title })
        val rel = db.dao().getAllRelations()
        assertEquals(1, rel.size)
        assertEquals(keep1, rel[0].firstIdeaId)
    }
}

@RunWith(AndroidJUnit4::class)
class SnapshotAndGraphQueryTest {
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db)
    }

    @After fun tearDown() { db.close() }

    @Test fun snapshotContainsEverythingForBackup() = runBlocking {
        val g = (repo.createGalaxy("مجرتي", 1) as SaveResult.Success).value
        val a = (repo.addIdea("أ", "نص أ", g) as SaveResult.Success).value
        val b = (repo.addIdea("ب", null, g) as SaveResult.Success).value
        repo.addRelation(a, b, RelationType.SIMILARITY)
        val snap = (repo.loadSnapshot() as SaveResult.Success).value
        assertEquals(1, snap.galaxies.size)
        assertEquals(2, snap.notes.size)
        assertEquals(1, snap.relations.size)
    }

    @Test fun graphRelationsOnlyIncludeLinksInsideTheGalaxy() = runBlocking {
        val g1 = (repo.createGalaxy("1", 0) as SaveResult.Success).value
        val g2 = (repo.createGalaxy("2", 1) as SaveResult.Success).value
        val a = (repo.addIdea("أ", null, g1) as SaveResult.Success).value
        val b = (repo.addIdea("ب", null, g1) as SaveResult.Success).value
        val c = (repo.addIdea("ج", null, g2) as SaveResult.Success).value
        repo.addRelation(a, b, RelationType.CAUSE)   // داخل g1
        repo.addRelation(a, c, RelationType.CAUSE)   // بين مجرتين: لا يظهر في رسم أي منهما
        assertEquals(1, repo.observeRelationsInGalaxy(g1).first().size)
        assertEquals(0, repo.observeRelationsInGalaxy(g2).first().size)
    }
}

@RunWith(AndroidJUnit4::class)
class CategoryBehaviourTest {
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun id(r: SaveResult<Long>): Long = (r as SaveResult.Success).value

    @Test fun searchingAGalaxyNameReturnsEverythingInsideIt() = runBlocking {
        val cooking = id(repo.createGalaxy("طبخ", 1))
        val computer = id(repo.createGalaxy("حاسوب", 2))
        id(repo.addIdea("كبسة", "أرز ولحم", cooking))
        id(repo.addIdea("منسف", null, cooking))
        id(repo.addIdea("تثبيت ويندوز", null, computer))
        // الكلمة «طبخ» ليست في نص أي ملاحظة، لكن المجرة تحمل هذا الاسم
        assertEquals(setOf("كبسة", "منسف"), repo.observeIdeas("طبخ").first().map { it.title }.toSet())
        assertEquals(listOf("تثبيت ويندوز"), repo.observeIdeas("حاسوب").first().map { it.title })
    }

    @Test fun movingANoteKeepsItsTextDatesAndLinks() = runBlocking {
        val a = id(repo.createGalaxy("أ", 0))
        val b = id(repo.createGalaxy("ب", 1))
        val n1 = id(repo.addIdea("ملاحظة", "نص", a))
        val n2 = id(repo.addIdea("أخرى", null, a))
        repo.addRelation(n1, n2, RelationType.SIMILARITY)
        val before = db.dao().getById(n1)!!
        assertTrue(repo.moveIdea(n1, b) is SaveResult.Success)
        val after = db.dao().getById(n1)!!
        assertEquals(b, after.galaxyId)
        assertEquals(before.title, after.title)
        assertEquals(before.description, after.description)
        assertEquals(before.updatedAt, after.updatedAt)
        assertEquals(1, db.dao().getAllRelations().size)
    }

    @Test fun movingToAMissingGalaxyOrOfAMissingNoteFails() = runBlocking {
        val g = id(repo.createGalaxy("أ", 0))
        val n = id(repo.addIdea("x", null, g))
        assertEquals(IdeaError.GalaxyNotFound, (repo.moveIdea(n, 999) as SaveResult.Failure).error)
        assertEquals(IdeaError.IdeaNotFound, (repo.moveIdea(999, g) as SaveResult.Failure).error)
        assertEquals(g, db.dao().getById(n)!!.galaxyId)
    }
}

@RunWith(AndroidJUnit4::class)
class ImportAndRejectionTest {
    private lateinit var context: Context
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun id(r: SaveResult<Long>): Long = (r as SaveResult.Success).value

    private suspend fun exportedBackup() = run {
        val g = id(repo.createGalaxy("طبخ", 1))
        val n1 = id(repo.addIdea("كبسة", "أرز", g))
        val n2 = id(repo.addIdea("منسف", null, g))
        repo.addRelation(n1, n2, RelationType.CAUSE)
        val snap = (repo.loadSnapshot() as SaveResult.Success).value
        (BackupParser.parse(BackupJson.toJson(snap.galaxies, snap.notes, snap.relations, 1L)) as BackupParser.Result.Ok).backup
    }

    @Test fun importIntoAnEmptyDatabaseRestoresEverything() = runBlocking {
        val backup = exportedBackup()
        val db2 = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        try {
            val report = (IdeaRepository(db2).importBackup(backup) as SaveResult.Success).value
            assertEquals(1, report.galaxiesAdded)
            assertEquals(2, report.notesAdded)
            assertEquals(0, report.notesSkipped)
            assertEquals(1, report.relationsAdded)
            assertEquals(2, db2.dao().getAllIdeas().size)
            assertEquals(1, db2.dao().getAllRelations().size)
        } finally {
            db2.close()
        }
    }

    @Test fun importingTheSameFileTwiceDoesNotDuplicateAnything() = runBlocking {
        val backup = exportedBackup() // البيانات الأصلية موجودة أصلًا في هذه القاعدة
        val report = (repo.importBackup(backup) as SaveResult.Success).value
        assertEquals(0, report.galaxiesAdded)
        assertEquals(0, report.notesAdded)
        assertEquals(2, report.notesSkipped)
        assertEquals(0, report.relationsAdded)
        assertEquals(2, db.dao().getAllIdeas().size)
        assertEquals(1, db.dao().getAllRelations().size)
        assertEquals(1, db.dao().getAllGalaxies().size)
    }

    @Test fun importNeverTouchesExistingNotes() = runBlocking {
        val backup = exportedBackup()
        val mine = id(repo.addIdea("ملاحظتي الخاصة", "لا تُمس"))
        val before = db.dao().getById(mine)!!
        repo.importBackup(backup)
        assertEquals(before, db.dao().getById(mine))
    }

    @Test fun rejectedSuggestionsArePersistedAndSurviveReopening() = runBlocking {
        val name = "rejected-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, IdeaBridgeDatabase::class.java, name).build()
        val first = open()
        try {
            IdeaRepository(first).rejectLink("1-2")
            IdeaRepository(first).rejectLink("1-2") // التكرار آمن
        } finally {
            first.close()
        }
        val second = open()
        try {
            assertEquals(setOf("1-2"), IdeaRepository(second).observeRejected().first())
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun allLinkRowsIncludeLinksBetweenTwoGalaxies() = runBlocking {
        val a = id(repo.createGalaxy("أ", 0))
        val b = id(repo.createGalaxy("ب", 1))
        val n1 = id(repo.addIdea("في أ", null, a))
        val n2 = id(repo.addIdea("في ب", null, b))
        repo.addRelation(n1, n2, RelationType.SIMILARITY)
        val row = repo.observeAllLinks().first().single()
        assertEquals("أ", row.firstGalaxy)
        assertEquals("ب", row.secondGalaxy)
    }
}

@RunWith(AndroidJUnit4::class)
class UndoDeleteTest {
    private lateinit var db: IdeaBridgeDatabase
    private lateinit var repo: IdeaRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IdeaBridgeDatabase::class.java).build()
        repo = IdeaRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun id(r: SaveResult<Long>): Long = (r as SaveResult.Success).value
    private fun snap(r: SaveResult<DeletedSnapshot>): DeletedSnapshot = (r as SaveResult.Success).value

    @Test fun undoingANoteDeletionRestoresSameIdTextAndLinks() = runBlocking {
        val g = id(repo.createGalaxy("أ", 0))
        val n1 = id(repo.addIdea("ملاحظة", "نص", g))
        val n2 = id(repo.addIdea("أخرى", null, g))
        repo.addRelation(n1, n2, RelationType.CAUSE)
        val noteBefore = db.dao().getById(n1)!!
        val relationsBefore = db.dao().getAllRelations()

        val captured = snap(repo.captureNote(n1))
        assertTrue(repo.deleteIdea(n1) is SaveResult.Success)
        assertNull(db.dao().getById(n1))
        assertTrue(db.dao().getAllRelations().isEmpty())

        assertTrue(repo.restore(captured) is SaveResult.Success)
        assertEquals(noteBefore, db.dao().getById(n1))
        assertEquals(relationsBefore, db.dao().getAllRelations())
    }

    @Test fun undoingAGalaxyDeletionRestoresEverythingInsideIt() = runBlocking {
        val a = id(repo.createGalaxy("أ", 2))
        val b = id(repo.createGalaxy("ب", 3))
        val n1 = id(repo.addIdea("1", "نص", a))
        val n2 = id(repo.addIdea("2", null, a))
        val keep = id(repo.addIdea("3", null, b))
        repo.addRelation(n1, n2, RelationType.SIMILARITY)
        repo.addRelation(n1, keep, RelationType.DEPENDENCY)
        val ideasBefore = db.dao().getAllIdeas()
        val relationsBefore = db.dao().getAllRelations()
        val galaxiesBefore = db.dao().getAllGalaxies()

        val captured = snap(repo.captureGalaxy(a))
        assertTrue(repo.deleteGalaxy(a) is SaveResult.Success)
        assertEquals(1, db.dao().getAllIdeas().size)

        assertTrue(repo.restore(captured) is SaveResult.Success)
        assertEquals(galaxiesBefore, db.dao().getAllGalaxies())
        assertEquals(ideasBefore, db.dao().getAllIdeas())
        assertEquals(relationsBefore, db.dao().getAllRelations())
    }

    @Test fun undoingARelationDeletionBringsItBack() = runBlocking {
        val g = id(repo.createGalaxy("أ", 0))
        val n1 = id(repo.addIdea("1", null, g))
        val n2 = id(repo.addIdea("2", null, g))
        val rel = id(repo.addRelation(n1, n2, RelationType.MERGE))
        val before = db.dao().getAllRelations()
        val captured = snap(repo.captureRelation(rel))
        repo.deleteRelation(rel)
        assertTrue(db.dao().getAllRelations().isEmpty())
        repo.restore(captured)
        assertEquals(before, db.dao().getAllRelations())
    }

    @Test fun restoreSkipsALinkWhoseOtherEndNoLongerExists() = runBlocking {
        val g = id(repo.createGalaxy("أ", 0))
        val n1 = id(repo.addIdea("1", null, g))
        val n2 = id(repo.addIdea("2", null, g))
        repo.addRelation(n1, n2, RelationType.CAUSE)
        val captured = snap(repo.captureNote(n1))
        repo.deleteIdea(n1)
        repo.deleteIdea(n2) // حُذف الطرف الآخر بعد ذلك
        assertTrue(repo.restore(captured) is SaveResult.Success)
        assertNotNull(db.dao().getById(n1))
        assertTrue(db.dao().getAllRelations().isEmpty())
    }

    @Test fun restoreMovesANoteToAnExistingGalaxyWhenItsGalaxyIsGone() = runBlocking {
        val gone = id(repo.createGalaxy("ستُحذف", 0))
        val other = id(repo.createGalaxy("باقية", 1))
        val n = id(repo.addIdea("ملاحظة", null, gone))
        val captured = snap(repo.captureNote(n))
        repo.deleteIdea(n)
        repo.deleteGalaxy(gone)
        assertTrue(repo.restore(captured) is SaveResult.Success)
        assertEquals(other, db.dao().getById(n)!!.galaxyId)
    }

    @Test fun restoreNeverOverwritesWhatExistsNow() = runBlocking {
        val g = id(repo.createGalaxy("أ", 0))
        val n = id(repo.addIdea("قديم", "نص", g))
        val captured = snap(repo.captureNote(n))
        repo.updateIdea(n, "معدّل", "نص جديد")
        repo.restore(captured)
        assertEquals("معدّل", db.dao().getById(n)!!.title)
    }

    @Test fun capturingSomethingMissingFailsAndDeletesNothing() = runBlocking {
        assertEquals(IdeaError.IdeaNotFound, (repo.captureNote(999) as SaveResult.Failure).error)
        assertEquals(IdeaError.GalaxyNotFound, (repo.captureGalaxy(999) as SaveResult.Failure).error)
        assertEquals(IdeaError.RelationNotFound, (repo.captureRelation(999) as SaveResult.Failure).error)
    }
}

/** يتحقق من بقاء البيانات بعد إغلاق قاعدة البيانات وإعادة فتح الملف نفسه (محاكاة إعادة تشغيل التطبيق). */
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun dataSurvivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)

        fun open() = Room.databaseBuilder(context, IdeaBridgeDatabase::class.java, name).build()

        val db1 = open()
        val repo1 = IdeaRepository(db1)
        val a = (repo1.addIdea("فكرة باقية", "وصف") as SaveResult.Success).value
        val b = (repo1.addIdea("فكرة ثانية", null) as SaveResult.Success).value
        repo1.addRelation(a, b, RelationType.DEPENDENCY)
        db1.close()

        val db2 = open()
        try {
            assertEquals(2, db2.dao().getAllIdeas().size)
            val relations = db2.dao().getAllRelations()
            assertEquals(1, relations.size)
            assertEquals(RelationType.DEPENDENCY, relations[0].type)
            assertEquals("فكرة باقية", db2.dao().getById(a)!!.title)
        } finally {
            db2.close()
            context.deleteDatabase(name)
        }
    }
}
