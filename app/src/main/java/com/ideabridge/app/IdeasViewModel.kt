package com.ideabridge.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ideabridge.app.data.GalaxyEntity
import com.ideabridge.app.data.GalaxyRow
import com.ideabridge.app.data.IdeaBridgeDatabase
import com.ideabridge.app.data.IdeaEntity
import com.ideabridge.app.data.IdeaRepository
import com.ideabridge.app.data.RelationEntity
import com.ideabridge.app.data.RelationRow
import com.ideabridge.app.data.RelationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.ideabridge.app.engine.BackupJson
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

    /** اقتراحات مرفوضة في هذه الجلسة فقط (تعود بعد إعادة تشغيل التطبيق). */
    private val rejected = MutableStateFlow<Set<String>>(emptySet())

    fun galaxyRelations(galaxyId: Long): Flow<List<RelationEntity>> = repo.observeRelationsInGalaxy(galaxyId)

    /** يحسب الاقتراحات خارج الخيط الرئيسي كلما تغيرت الملاحظات أو الروابط أو المرفوضات. */
    fun linkSuggestions(galaxyId: Long): Flow<List<LinkSuggestion>> =
        combine(repo.observeIdeasInGalaxy(galaxyId, ""), repo.observeRelationsInGalaxy(galaxyId), rejected) { notes, rels, rej ->
            val docs = notes.map { IdeaDoc(it.id, it.title, it.description) }
            val existing = rels.map { pairKey(it.firstIdeaId, it.secondIdeaId) }.toSet()
            engine.suggest(docs, existing, rej)
        }.flowOn(Dispatchers.Default)

    fun rejectSuggestion(key: String) { rejected.update { it + key } }

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

    fun createGalaxy(name: String, colorIndex: Int, onResult: (SaveResult<Long>) -> Unit) {
        viewModelScope.launch { onResult(repo.createGalaxy(name, colorIndex)) }
    }
    fun updateGalaxy(id: Long, name: String, colorIndex: Int, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.updateGalaxy(id, name, colorIndex)) }
    }
    fun deleteGalaxy(id: Long, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteGalaxy(id)) }
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

    fun deleteIdea(id: Long, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteIdea(id)) }
    }
    fun addRelation(first: Long, second: Long, type: RelationType, onResult: (SaveResult<Long>) -> Unit) {
        viewModelScope.launch { onResult(repo.addRelation(first, second, type)) }
    }
    fun deleteRelation(id: Long, onResult: (SaveResult<Unit>) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteRelation(id)) }
    }
}
