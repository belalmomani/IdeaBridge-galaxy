package com.ideabridge.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.DeletedSnapshot
import com.ideabridge.app.data.GalaxyRow
import com.ideabridge.app.data.ImportOutcome
import com.ideabridge.app.data.LinkRow
import com.ideabridge.app.data.IdeaBridgeDatabase
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.IdeaRepository
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.data.RelationRow
import com.ideabridge.app.data.RelationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.ideabridge.app.engine.BackupJson
import com.ideabridge.app.engine.BackupParser
import com.ideabridge.app.engine.GalaxyDoc
import com.ideabridge.app.engine.GalaxyMatcher
import com.ideabridge.app.engine.IdeaDoc
import com.ideabridge.app.engine.LinkEngine
import com.ideabridge.app.engine.LinkSuggestion
import com.ideabridge.app.engine.pairKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مجرة مقترحة لملاحظة، جاهزة للعرض في المحرر. */
data class GalaxyMove(val galaxyId: Long, val galaxyName: String, val words: List<String>)

/** غلاف يميّز "لم يُحمَّل بعد" (null) عن "حُمِّل ولا توجد قيمة" (Loaded(null)). */
data class Loaded<T>(val value: T?)

@OptIn(ExperimentalCoroutinesApi::class)
class IdeasViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = IdeaRepository(IdeaBridgeDatabase.get(app))

    init {
        // لا تبقى الشاشة بلا مجرة عند أول تشغيل
        viewModelScope.launch { repo.ensureDefaultGalaxy() }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(value: String) { _query.value = value }

    /** كل الملاحظات مع البحث العام؛ null = جارٍ التحميل. */
    val ideas: StateFlow<List<IdeaEntity>?> = _query
        .flatMapLatest { repo.observeIdeas(it) }
        .map<List<IdeaEntity>, List<IdeaEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** كل الملاحظات بلا بحث (لمنتقي العلاقات). */
    val allIdeas: StateFlow<List<IdeaEntity>?> = repo.observeIdeas("")
        .map<List<IdeaEntity>, List<IdeaEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val galaxies: StateFlow<List<GalaxyRow>?> = repo.observeGalaxies()
        .map<List<GalaxyRow>, List<GalaxyRow>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun galaxy(id: Long): Flow<Loaded<GalaxyEntity>> = repo.observeGalaxy(id).map { Loaded(it) }
    fun notesIn(galaxyId: Long, query: String): Flow<List<IdeaEntity>> = repo.observeIdeasInGalaxy(galaxyId, query)
    fun idea(id: Long): Flow<Loaded<IdeaEntity>> = repo.observeIdea(id).map { Loaded(it) }
    fun relations(ideaId: Long): Flow<List<RelationRow>> =
        if (ideaId == 0L) emptyFlow() else repo.observeRelations(ideaId)

    private val engine = LinkEngine()
    private val matcher = GalaxyMatcher()

    /** روابط نصية بين المجرات تُحسب خارج الخيط الرئيسي كلما تغيرت المجرات أو الملاحظات. */
    val galaxyLinks: StateFlow<List<LinkSuggestion>> =
        combine(repo.observeGalaxies(), repo.observeIdeas("")) { galaxies, notes ->
            val byGalaxy = notes.groupBy { it.galaxyId }
            matcher.galaxyLinks(galaxies.map { g ->
                GalaxyDoc(g.id, g.name, byGalaxy[g.id].orEmpty().map { IdeaDoc(it.id, it.title, it.description) })
            })
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** أنسب مجرة لملاحظة محفوظة، أو null. لا يُنقل شيء إلا بموافقة المستخدم. */
    suspend fun suggestGalaxy(noteId: Long): GalaxyMove? {
        val snap = (repo.loadSnapshot() as? SaveResult.Success)?.value ?: return null
        return withContext(Dispatchers.Default) {
            val note = snap.notes.firstOrNull { it.id == noteId } ?: return@withContext null
            val galaxies = snap.galaxies.map { g ->
                GalaxyDoc(
                    g.id, g.name,
                    snap.notes.filter { it.galaxyId == g.id && it.id != noteId }
                        .map { IdeaDoc(it.id, it.title, it.description) },
                )
            }
            val match = matcher.suggestFor(IdeaDoc(note.id, note.title, note.description), note.galaxyId, galaxies)
                ?: return@withContext null
            val name = snap.galaxies.firstOrNull { it.id == match.galaxyId }?.name ?: return@withContext null
            GalaxyMove(match.galaxyId, name, match.matchedWords)
        }
    }

    fun moveNote(noteId: Long, galaxyId: Long, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.moveIdea(noteId, galaxyId)) }
    }


    fun galaxyRelations(galaxyId: Long): Flow<List<RelationEntity>> = repo.observeRelationsInGalaxy(galaxyId)

    /** يحسب الاقتراحات خارج الخيط الرئيسي كلما تغيرت الملاحظات أو الروابط أو المرفوضات. */
    fun linkSuggestions(galaxyId: Long): Flow<List<LinkSuggestion>> =
        combine(repo.observeIdeasInGalaxy(galaxyId, ""), repo.observeRelationsInGalaxy(galaxyId), repo.observeRejected()) { notes, rels, rej ->
            val docs = notes.map { IdeaDoc(it.id, it.title, it.description) }
            val existing = rels.map { pairKey(it.firstIdeaId, it.secondIdeaId) }.toSet()
            engine.suggest(docs, existing, rej)
        }.flowOn(Dispatchers.Default)

    /** الرفض يُحفظ في قاعدة البيانات فلا يعود الاقتراح بعد إعادة التشغيل. */
    fun rejectSuggestion(key: String) {
        viewModelScope.launch { repo.rejectLink(key) }
    }

    val allLinks: StateFlow<List<LinkRow>?> = repo.observeAllLinks()
        .map<List<LinkRow>, List<LinkRow>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** قبول الاقتراح = إنشاء رابط "تشابه" عادي بآلية الروابط اليدوية نفسها (لا تكرار). */
    fun acceptSuggestion(s: LinkSuggestion, onResult: (SaveResult<Long>) -> Unit) {
        viewModelScope.launch { onResult(repo.addRelation(s.firstId, s.secondId, RelationType.SIMILARITY)) }
    }

    /** يكتب نسخة JSON احتياطية في الملف الذي اختاره المستخدم. */
    fun exportBackup(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                when (val snap = repo.loadSnapshot()) {
                    is SaveResult.Failure -> false
                    is SaveResult.Success -> withContext(Dispatchers.IO) {
                        val json = BackupJson.toJson(snap.value.galaxies, snap.value.notes, snap.value.relations, System.currentTimeMillis())
                        val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                            ?: return@withContext false
                        out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            onDone(ok)
        }
    }

    /** يقرأ ملف نسخة احتياطية ويضيف محتواه دون حذف شيء من البيانات الحالية. */
    fun importBackup(uri: android.net.Uri, onOutcome: (ImportOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome: ImportOutcome = try {
                val text = withContext(Dispatchers.IO) { readTextLimited(uri) }
                if (text == null) {
                    ImportOutcome.Invalid
                } else when (val parsed = BackupParser.parse(text)) {
                    is BackupParser.Result.Invalid -> ImportOutcome.Invalid
                    is BackupParser.Result.Ok -> when (val r = repo.importBackup(parsed.backup)) {
                        is SaveResult.Success -> ImportOutcome.Done(r.value)
                        is SaveResult.Failure -> ImportOutcome.Failed
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportOutcome.Failed
            }
            onOutcome(outcome)
        }
    }

    /** يقرأ الملف بحد أقصى للحجم؛ يعيد null إن كان أكبر من الحد أو تعذر فتحه. */
    private fun readTextLimited(uri: android.net.Uri): String? {
        val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > BackupParser.MAX_BYTES) return null
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    fun createGalaxy(name: String, colorIndex: Int, onResult: (SaveResult<Long>) -> Unit) {
        viewModelScope.launch { onResult(repo.createGalaxy(name, colorIndex)) }
    }
    fun updateGalaxy(id: Long, name: String, colorIndex: Int, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.updateGalaxy(id, name, colorIndex)) }
    }
    /** يحذف المجرة مع التقاط ما حُذف ليمكن التراجع. */
    fun deleteGalaxy(id: Long, onResult: (SaveResult<DeletedSnapshot>) -> Unit) {
        viewModelScope.launch { onResult(captureThenDelete(repo.captureGalaxy(id)) { repo.deleteGalaxy(id) }) }
    }

    /** حفظ تلقائي من المحرر: ينشئ الملاحظة إن كان noteId = 0 وإلا يحدّثها، ويعيد معرفها. */
    suspend fun saveNote(noteId: Long, galaxyId: Long, title: String, body: String): SaveResult<Long> =
        if (noteId == 0L) {
            repo.addIdea(title, body, galaxyId.takeIf { it != 0L })
        } else {
            when (val r = repo.updateIdea(noteId, title, body)) {
                is SaveResult.Success -> SaveResult.Success(noteId)
                is SaveResult.Failure -> r
            }
        }

    fun deleteIdea(id: Long, onResult: (SaveResult<DeletedSnapshot>) -> Unit) {
        viewModelScope.launch { onResult(captureThenDelete(repo.captureNote(id)) { repo.deleteIdea(id) }) }
    }
    fun addRelation(first: Long, second: Long, type: RelationType, onResult: (SaveResult<Long>) -> Unit) {
        viewModelScope.launch { onResult(repo.addRelation(first, second, type)) }
    }
    fun deleteRelation(id: Long, onResult: (SaveResult<DeletedSnapshot>) -> Unit) {
        viewModelScope.launch { onResult(captureThenDelete(repo.captureRelation(id)) { repo.deleteRelation(id) }) }
    }

    fun restore(snapshot: DeletedSnapshot, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.restore(snapshot)) }
    }

    /** لا يُحذف شيء إلا بعد نجاح التقاطه، ولا يُعاد نجاح إلا إذا نجح الحذف فعلًا. */
    private suspend fun captureThenDelete(
        captured: SaveResult<DeletedSnapshot>,
        delete: suspend () -> SaveResult<Unit>,
    ): SaveResult<DeletedSnapshot> {
        if (captured is SaveResult.Failure) return captured
        val snapshot = (captured as SaveResult.Success).value
        return when (val deleted = delete()) {
            is SaveResult.Success -> SaveResult.Success(snapshot)
            is SaveResult.Failure -> deleted
        }
    }
}
