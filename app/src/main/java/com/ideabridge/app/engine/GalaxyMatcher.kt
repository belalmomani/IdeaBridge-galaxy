package com.ideabridge.app.engine

/** مجرة كما يراها المحرك: معرف واسم وملاحظاتها (اسم المجرة هو التصنيف). */
data class GalaxyDoc(val id: Long, val name: String, val notes: List<IdeaDoc>)

/** مجرة مقترحة لملاحظة، مع الكلمات التي استند إليها الاقتراح. */
data class GalaxyMatch(val galaxyId: Long, val relevance: Int, val matchedWords: List<String>)

/**
 * يربط الملاحظات بالمجرات (التصنيفات) بالمقارنة النصية، وكذلك المجرات ببعضها.
 * الأسلوب قابل للتفسير: ورود اسم المجرة في الملاحظة (عنوانًا أقوى من نص)، وتشابه الملاحظة مع محتوى المجرة.
 */
class GalaxyMatcher(private val config: EngineConfig = EngineConfig()) {

    /**
     * يقترح مجرة أنسب من مجرة الملاحظة الحالية، أو null إن لم تتوفر أدلة كافية.
     * relevance = (60 إن ورد اسم المجرة في العنوان، أو 40 إن ورد في النص) + درجة التشابه مع محتواها، بحد أقصى 100.
     * يُقترح النقل فقط إذا بلغت 40 على الأقل وزادت 20 على الأقل عن المجرة الحالية.
     */
    fun suggestFor(note: IdeaDoc, currentGalaxyId: Long, galaxies: List<GalaxyDoc>): GalaxyMatch? {
        val list = galaxies.distinctBy { it.id }
        if (list.size < 2) return null
        val noteDoc = IdeaDoc(NOTE_DOC_ID, note.title, note.description)
        val entries = KeywordIndex(listOf(noteDoc) + list.map { galaxyText(it) }, config).entries
        val noteEntry = entries[0]
        val titleTokens = ArabicText.tokens(note.title, config.stopWords).map { it.norm }.toSet()
        val bodyTokens = ArabicText.tokens(note.description.orEmpty(), config.stopWords).map { it.norm }.toSet()

        val matches = list.mapIndexed { i, galaxy ->
            val nameTokens = ArabicText.tokens(galaxy.name, config.stopWords)
            val titleHits = nameTokens.filter { it.norm in titleTokens }
            val bodyHits = nameTokens.filter { it.norm in bodyTokens }
            val bonus = when {
                titleHits.isNotEmpty() -> 60
                bodyHits.isNotEmpty() -> 40
                else -> 0
            }
            val galaxyEntry = entries[i + 1]
            val relevance = minOf(100, bonus + scoreOf(noteEntry, galaxyEntry))
            val words = (titleHits + bodyHits).map { it.original } + sharedTerms(noteEntry, galaxyEntry, 3)
            GalaxyMatch(galaxy.id, relevance, words.distinct().take(4))
        }
        val best = matches.maxByOrNull { it.relevance } ?: return null
        val current = matches.firstOrNull { it.galaxyId == currentGalaxyId }?.relevance ?: 0
        if (best.galaxyId == currentGalaxyId) return null
        if (best.relevance < MIN_RELEVANCE || best.relevance - current < MIN_MARGIN) return null
        return best
    }

    /** روابط نصية بين المجرات: تتشارك كلمات في أسمائها وملاحظاتها (عتبة أخفض لأن نص المجرة مجمّع). */
    fun galaxyLinks(galaxies: List<GalaxyDoc>): List<LinkSuggestion> =
        LinkEngine(config.copy(similarityThreshold = GALAXY_LINK_THRESHOLD))
            .suggest(galaxies.distinctBy { it.id }.map { galaxyText(it) })

    private fun galaxyText(g: GalaxyDoc): IdeaDoc = IdeaDoc(
        id = g.id,
        title = g.name,
        description = g.notes.joinToString(" ") { it.title + " " + it.description.orEmpty().take(500) }.take(6000),
    )

    companion object {
        const val NOTE_DOC_ID = -1L
        const val MIN_RELEVANCE = 40
        const val MIN_MARGIN = 20
        const val GALAXY_LINK_THRESHOLD = 20
    }
}
