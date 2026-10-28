package com.ideabridge.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ideabridge.app.data.IdeaBridgeDatabase
import com.ideabridge.app.data.IdeaRepository
import com.ideabridge.app.data.RelationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
