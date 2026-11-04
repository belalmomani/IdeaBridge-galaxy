package com.ideabridge.app.data

import androidx.room.withTransaction
import com.ideabridge.app.IdeaError
import com.ideabridge.app.IdeaException
import com.ideabridge.app.IdeaRules
import com.ideabridge.app.engine.BackupJson
import com.ideabridge.app.engine.BackupParser
import com.ideabridge.app.SaveResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Snapshot(
    val galaxies: List<GalaxyEntity>,
    val notes: List<IdeaEntity>,
    val relations: List<RelationEntity>,
)

class IdeaRepository(
    private val db: IdeaBridgeDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    fun observeIdeas(query: String): Flow<List<IdeaEntity>> = dao.observe(IdeaRules.likePattern(query))
    fun observeIdea(id: Long): Flow<IdeaEntity?> = dao.observeById(id)
    fun observeRelations(ideaId: Long): Flow<List<RelationRow>> = dao.observeRelationsOf(ideaId)

    /** galaxyId = null → المجرة الافتراضية (تُنشأ عند الحاجة). */
    suspend fun addIdea(title: String, description: String?, galaxyId: Long? = null): SaveResult<Long> {
        IdeaRules.validateIdea(title, description)?.let { return SaveResult.Failure(it) }
        return safely {
            db.withTransaction {
                val gid = galaxyId ?: ensureDefaultGalaxyInternal()
                if (dao.getGalaxy(gid) == null) throw IdeaException(IdeaError.GalaxyNotFound)
                val now = clock()
                dao.insertIdea(
                    IdeaEntity(
                        title = IdeaRules.cleanTitle(title),
                        description = IdeaRules.cleanDescription(description),
                        createdAt = now, updatedAt = now, galaxyId = gid,
                    )
                )
            }
        }
    }

    fun observeRelationsInGalaxy(galaxyId: Long): Flow<List<RelationEntity>> = dao.observeRelationsInGalaxy(galaxyId)

    /** لقطة كاملة من البيانات للنسخة الاحتياطية. */
    suspend fun loadSnapshot(): SaveResult<Snapshot> = safely {
        db.withTransaction { Snapshot(dao.getAllGalaxies(), dao.getAllIdeas(), dao.getAllRelations()) }
    }

    /** تثبيت/إلغاء تثبيت ملاحظة دون تغيير نصها أو تاريخ تعديلها. */
    suspend fun setPinned(id: Long, pinned: Boolean): SaveResult<Unit> = safely {
        if (dao.setPinned(id, pinned) == 0) throw IdeaException(IdeaError.IdeaNotFound)
    }

    // ---------- التقاط واستعادة (التراجع عن الحذف) ----------
    suspend fun captureNote(id: Long): SaveResult<DeletedSnapshot> = safely {
        db.withTransaction {
            val note = dao.getById(id) ?: throw IdeaException(IdeaError.IdeaNotFound)
            DeletedSnapshot(null, listOf(note), dao.getRelationsOfIdea(id))
        }
    }

    suspend fun captureGalaxy(id: Long): SaveResult<DeletedSnapshot> = safely {
        db.withTransaction {
            val galaxy = dao.getGalaxy(id) ?: throw IdeaException(IdeaError.GalaxyNotFound)
            DeletedSnapshot(galaxy, dao.getIdeasOfGalaxy(id), dao.getRelationsOfGalaxy(id))
        }
    }

    suspend fun captureRelation(id: Long): SaveResult<DeletedSnapshot> = safely {
        val relation = dao.getRelation(id) ?: throw IdeaException(IdeaError.RelationNotFound)
        DeletedSnapshot(null, emptyList(), listOf(relation))
    }

    /**
     * يعيد ما حُذف بمعرفاته الأصلية. لا يستبدل شيئًا موجودًا (IGNORE). ملاحظة مجرتها لم تعد موجودة تذهب إلى
     * المجرة الافتراضية، ورابط أحد طرفيه غير موجود لا يُعاد.
     */
    suspend fun restore(snapshot: DeletedSnapshot): SaveResult<Unit> = safely {
        db.withTransaction {
            restoreInternal(snapshot)
            if (snapshot.trashId != 0L) dao.deleteTrash(snapshot.trashId) // تراجع فوري: لا نسخة مكررة في السلة
        }
    }

    private suspend fun restoreInternal(snapshot: DeletedSnapshot) {
            snapshot.galaxy?.let { dao.restoreGalaxy(it) }
            val notes = snapshot.notes.map { n ->
                if (dao.getGalaxy(n.galaxyId) == null) n.copy(galaxyId = ensureDefaultGalaxyInternal()) else n
            }
            dao.restoreIdeas(notes)
            val relations = snapshot.relations.filter {
                dao.getById(it.firstIdeaId) != null && dao.getById(it.secondIdeaId) != null
            }
            dao.restoreRelations(relations)
    }

    // ---------- سلة المحذوفات ----------
    fun observeTrash(): Flow<List<TrashRow>> = dao.observeTrash()

    private suspend fun captureNoteInternal(id: Long): DeletedSnapshot {
        val note = dao.getById(id) ?: throw IdeaException(IdeaError.IdeaNotFound)
        return DeletedSnapshot(null, listOf(note), dao.getRelationsOfIdea(id))
    }

    private suspend fun captureGalaxyInternal(id: Long): DeletedSnapshot {
        val galaxy = dao.getGalaxy(id) ?: throw IdeaException(IdeaError.GalaxyNotFound)
        return DeletedSnapshot(galaxy, dao.getIdeasOfGalaxy(id), dao.getRelationsOfGalaxy(id))
    }

    /** يحفظ نسخة كاملة مما حُذف في السلة؛ يعيد 0 إن كان حجمها أكبر من الحد (يبقى التراجع الفوري متاحًا). */
    private suspend fun storeInTrash(snapshot: DeletedSnapshot, kind: String, title: String): Long {
        val galaxies = snapshot.galaxy?.let { listOf(it) }
            ?: snapshot.notes.map { it.galaxyId }.distinct().mapNotNull { dao.getGalaxy(it) }
        val payload = BackupJson.toJson(galaxies, snapshot.notes, snapshot.relations, clock())
        if (payload.length > MAX_TRASH_PAYLOAD) return 0L
        return dao.insertTrash(
            TrashEntity(kind = kind, title = title, noteCount = snapshot.notes.size, deletedAt = clock(), payload = payload)
        )
    }

    /** يحذف الملاحظة ويحفظها في السلة في معاملة واحدة، ويعيد ما حُذف للتراجع الفوري. */
    suspend fun deleteNoteToTrash(id: Long): SaveResult<DeletedSnapshot> = safely {
        db.withTransaction {
            val snapshot = captureNoteInternal(id)
            dao.deleteRelationsOf(id)
            if (dao.deleteIdea(id) == 0) throw IdeaException(IdeaError.IdeaNotFound)
            snapshot.copy(trashId = storeInTrash(snapshot, TrashKind.NOTE, snapshot.notes.first().title))
        }
    }

    /** يحذف المجرة وملاحظاتها وروابطها ويحفظها في السلة في معاملة واحدة. */
    suspend fun deleteGalaxyToTrash(id: Long): SaveResult<DeletedSnapshot> = safely {
        db.withTransaction {
            val snapshot = captureGalaxyInternal(id)
            dao.deleteRelationsOfGalaxy(id)
            dao.deleteIdeasOfGalaxy(id)
            if (dao.deleteGalaxy(id) == 0) throw IdeaException(IdeaError.GalaxyNotFound)
            snapshot.copy(trashId = storeInTrash(snapshot, TrashKind.GALAXY, snapshot.galaxy?.name.orEmpty()))
        }
    }

    /** يعيد عنصرًا من السلة بمعرفاته وتواريخه، ثم يزيله منها. لا يستبدل شيئًا موجودًا. */
    suspend fun restoreFromTrash(trashId: Long): SaveResult<Unit> = safely {
        db.withTransaction {
            val payload = dao.getTrashPayload(trashId) ?: throw IdeaException(IdeaError.TrashItemNotFound)
            val parsed = (BackupParser.parse(payload) as? BackupParser.Result.Ok)?.backup
                ?: throw IdeaException(IdeaError.Unknown)
            restoreInternal(snapshotOf(parsed))
            dao.deleteTrash(trashId)
        }
    }

    private fun snapshotOf(backup: ParsedBackup): DeletedSnapshot = DeletedSnapshot(
        galaxy = backup.galaxies.firstOrNull()?.let { GalaxyEntity(it.id, it.name, it.colorIndex, it.createdAt) },
        notes = backup.notes.map {
            IdeaEntity(
                id = it.id, title = it.title, description = IdeaRules.cleanDescription(it.body),
                createdAt = it.createdAt, updatedAt = it.updatedAt, galaxyId = it.galaxyId, pinned = it.pinned,
            )
        },
        relations = backup.relations.map {
            RelationEntity(firstIdeaId = it.firstNoteId, secondIdeaId = it.secondNoteId, type = it.type, createdAt = clock())
        },
    )

    suspend fun deleteForever(trashId: Long): SaveResult<Unit> = safely {
        if (dao.deleteTrash(trashId) == 0) throw IdeaException(IdeaError.TrashItemNotFound)
    }

    suspend fun emptyTrash(): SaveResult<Unit> = safely { dao.clearTrash(); Unit }

    /** يحذف نهائيًا ما مضى عليه في السلة أكثر من 30 يومًا. */
    suspend fun purgeTrash(): SaveResult<Unit> = safely { dao.purgeTrashBefore(clock() - TRASH_RETENTION_MS); Unit }

    // ---------- اقتراحات مرفوضة وروابط ----------
    fun observeRejected(): Flow<Set<String>> = dao.observeRejectedKeys().map { it.toSet() }
    fun observeAllLinks(): Flow<List<LinkRow>> = dao.observeAllLinkRows()

    suspend fun rejectLink(pairKey: String): SaveResult<Unit> =
        safely { dao.insertRejected(RejectedLinkEntity(pairKey, clock())) }

    /**
     * يضيف محتوى نسخة احتياطية إلى البيانات الحالية دون حذف أو استبدال أي شيء:
     * المجرة بالاسم نفسه تُعاد استخدامها، والملاحظة المطابقة (العنوان والنص في المجرة نفسها) تُتخطى،
     * والرابط الموجود يُتخطى. العملية كلها معاملة واحدة، فإن فشلت لم يتغير شيء.
     */
    suspend fun importBackup(backup: ParsedBackup): SaveResult<ImportReport> = safely {
        db.withTransaction {
            val galaxies = dao.getAllGalaxies().toMutableList()
            val galaxyIds = HashMap<Long, Long>()
            var galaxiesAdded = 0
            for (g in backup.galaxies) {
                val existing = galaxies.firstOrNull { it.name.trim().lowercase() == g.name.lowercase() }
                val id = if (existing != null) {
                    existing.id
                } else {
                    val color = Math.floorMod(g.colorIndex, IdeaRules.GALAXY_STYLE_COUNT)
                    val newId = dao.insertGalaxy(GalaxyEntity(name = g.name, colorIndex = color, createdAt = if (g.createdAt > 0) g.createdAt else clock()))
                    galaxies.add(GalaxyEntity(newId, g.name, color, g.createdAt))
                    galaxiesAdded++
                    newId
                }
                galaxyIds[g.id] = id
            }

            val notes = dao.getAllIdeas().toMutableList()
            val noteIds = HashMap<Long, Long>()
            var notesAdded = 0
            var notesSkipped = 0
            for (n in backup.notes) {
                val galaxyId = galaxyIds[n.galaxyId] ?: ensureDefaultGalaxyInternal()
                val duplicate = notes.firstOrNull {
                    it.galaxyId == galaxyId && it.title == n.title && (it.description ?: "") == n.body
                }
                if (duplicate != null) {
                    noteIds[n.id] = duplicate.id
                    notesSkipped++
                } else {
                    val created = if (n.createdAt > 0) n.createdAt else clock()
                    val updated = if (n.updatedAt > 0) n.updatedAt else created
                    val entity = IdeaEntity(
                        title = n.title, description = IdeaRules.cleanDescription(n.body),
                        createdAt = created, updatedAt = updated, galaxyId = galaxyId, pinned = n.pinned,
                    )
                    val newId = dao.insertIdea(entity)
                    notes.add(entity.copy(id = newId))
                    noteIds[n.id] = newId
                    notesAdded++
                }
            }

            var relationsAdded = 0
            for (r in backup.relations) {
                val first = noteIds[r.firstNoteId] ?: continue
                val second = noteIds[r.secondNoteId] ?: continue
                if (first == second || dao.countRelation(first, second, r.type) > 0) continue
                dao.insertRelation(RelationEntity(firstIdeaId = first, secondIdeaId = second, type = r.type, createdAt = clock()))
                relationsAdded++
            }
            ImportReport(galaxiesAdded, notesAdded, notesSkipped, relationsAdded)
        }
    }

    // ---------- المجرات ----------
    fun observeGalaxies(): Flow<List<GalaxyRow>> = dao.observeGalaxyRows()
    fun observeGalaxy(id: Long): Flow<GalaxyEntity?> = dao.observeGalaxyById(id)
    fun observeIdeasInGalaxy(galaxyId: Long, query: String): Flow<List<IdeaEntity>> =
        dao.observeInGalaxy(galaxyId, IdeaRules.likePattern(query))

    private suspend fun ensureDefaultGalaxyInternal(): Long =
        dao.firstGalaxyId() ?: dao.insertGalaxy(
            GalaxyEntity(name = IdeaBridgeDatabase.DEFAULT_GALAXY_NAME, colorIndex = 0, createdAt = clock())
        )

    /** يضمن وجود مجرة واحدة على الأقل ويعيد معرّف أولاها. */
    suspend fun ensureDefaultGalaxy(): SaveResult<Long> = safely { db.withTransaction { ensureDefaultGalaxyInternal() } }

    /** ينقل ملاحظة إلى مجرة أخرى دون تغيير نصها أو تاريخ تعديلها أو روابطها. */
    suspend fun moveIdea(id: Long, galaxyId: Long): SaveResult<Unit> = safely {
        db.withTransaction {
            if (dao.getGalaxy(galaxyId) == null) throw IdeaException(IdeaError.GalaxyNotFound)
            if (dao.moveIdea(id, galaxyId) == 0) throw IdeaException(IdeaError.IdeaNotFound)
        }
    }

    suspend fun createGalaxy(name: String, colorIndex: Int): SaveResult<Long> {
        IdeaRules.validateGalaxyName(name)?.let { return SaveResult.Failure(it) }
        return safely { dao.insertGalaxy(GalaxyEntity(name = name.trim(), colorIndex = colorIndex, createdAt = clock())) }
    }

    suspend fun updateGalaxy(id: Long, name: String, colorIndex: Int): SaveResult<Unit> {
        IdeaRules.validateGalaxyName(name)?.let { return SaveResult.Failure(it) }
        return safely {
            if (dao.updateGalaxy(id, name.trim(), colorIndex) == 0) throw IdeaException(IdeaError.GalaxyNotFound)
        }
    }

    /** يحذف المجرة وملاحظاتها وعلاقات تلك الملاحظات في معاملة واحدة. */
    suspend fun deleteGalaxy(id: Long): SaveResult<Unit> = safely {
        db.withTransaction {
            dao.deleteRelationsOfGalaxy(id)
            dao.deleteIdeasOfGalaxy(id)
            if (dao.deleteGalaxy(id) == 0) throw IdeaException(IdeaError.GalaxyNotFound)
        }
    }

    suspend fun updateIdea(id: Long, title: String, description: String?): SaveResult<Unit> {
        IdeaRules.validateIdea(title, description)?.let { return SaveResult.Failure(it) }
        return safely {
            val rows = dao.updateIdea(id, IdeaRules.cleanTitle(title), IdeaRules.cleanDescription(description), clock())
            if (rows == 0) throw IdeaException(IdeaError.IdeaNotFound)
        }
    }

    /** يحذف الفكرة وعلاقاتها معًا في معاملة واحدة. */
    suspend fun deleteIdea(id: Long): SaveResult<Unit> = safely {
        db.withTransaction {
            dao.deleteRelationsOf(id)
            if (dao.deleteIdea(id) == 0) throw IdeaException(IdeaError.IdeaNotFound)
        }
    }

    suspend fun addRelation(firstId: Long, secondId: Long, type: RelationType): SaveResult<Long> {
        IdeaRules.validateRelation(firstId, secondId)?.let { return SaveResult.Failure(it) }
        return safely {
            db.withTransaction {
                if (dao.getById(firstId) == null || dao.getById(secondId) == null) {
                    throw IdeaException(IdeaError.IdeaNotFound)
                }
                if (dao.countRelation(firstId, secondId, type) > 0) {
                    throw IdeaException(IdeaError.DuplicateRelation)
                }
                dao.insertRelation(
                    RelationEntity(firstIdeaId = firstId, secondIdeaId = secondId, type = type, createdAt = clock())
                )
            }
        }
    }

    suspend fun deleteRelation(id: Long): SaveResult<Unit> = safely {
        if (dao.deleteRelation(id) == 0) throw IdeaException(IdeaError.RelationNotFound)
    }

    // لا نعرض نجاحًا أبدًا إذا فشلت العملية فعليًا.
    private suspend fun <T> safely(block: suspend () -> T): SaveResult<T> =
        try {
            SaveResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IdeaException) {
            SaveResult.Failure(e.error)
        } catch (e: Exception) {
            SaveResult.Failure(IdeaError.Unknown)
        }

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_TRASH_PAYLOAD = 1_500_000
    }
}
