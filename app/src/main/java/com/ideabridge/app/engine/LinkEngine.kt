package com.ideabridge.app.engine

enum class LinkStrength { STRONG, MEDIUM, WEAK }

/** اقتراح رابط بين ملاحظتين، مبني فقط على الكلمات المشتركة؛ ليس فهمًا للمعنى ولا نتيجة مؤكدة. */
data class LinkSuggestion(
    val firstId: Long,
    val secondId: Long,
    /** درجة التشابه النصي من 0 إلى 100 (ليست احتمال صحة العلاقة). */
    val score: Int,
    val strength: LinkStrength,
    /** الكلمات المشتركة كما كُتبت في الملاحظة الأولى. */
    val sharedTerms: List<String>,
) {
    val key: String get() = pairKey(firstId, secondId)
}

/** مفتاح الزوج بلا اتجاه: نفس المفتاح للزوج (أ، ب) و(ب، أ). */
fun pairKey(a: Long, b: Long): String = "${minOf(a, b)}-${maxOf(a, b)}"

/**
 * محرك محلي وقابل للتفسير: TF-IDF مبسط + تشابه جيب التمام على الكلمات المميزة.
 * لا يتصل بالإنترنت ولا يعتمد على Android أو قاعدة البيانات.
 */
class LinkEngine(private val config: EngineConfig = EngineConfig()) {

    /** درجة التشابه (0..100) بين ملاحظتين ضمن مجموعة ملاحظات تُحسب منها أوزان الكلمات. */
    fun similarity(a: IdeaDoc, b: IdeaDoc, corpus: List<IdeaDoc> = emptyList()): Int {
        if (a.id == b.id) return 0
        val docs = (corpus + a + b).distinctBy { it.id }
        val entries = KeywordIndex(docs, config).entries.associateBy { it.doc.id }
        val ea = entries[a.id] ?: return 0
        val eb = entries[b.id] ?: return 0
        return scoreOf(ea, eb)
    }

    /**
     * يقترح روابط بين الملاحظات المعطاة.
     * @param existingPairs أزواج مرتبطة فعلًا (بمفتاح pairKey) تُستبعد.
     * @param rejectedKeys أزواج رفضها المستخدم وتُستبعد.
     */
    fun suggest(
        notes: List<IdeaDoc>,
        existingPairs: Set<String> = emptySet(),
        rejectedKeys: Set<String> = emptySet(),
    ): List<LinkSuggestion> {
        val docs = notes.distinctBy { it.id }.take(config.maxAnalyzedIdeas)
        if (docs.size < 2) return emptyList()
        val entries = KeywordIndex(docs, config).entries
        val out = ArrayList<LinkSuggestion>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (a.doc.id == b.doc.id) continue
                val key = pairKey(a.doc.id, b.doc.id)
                if (key in existingPairs || key in rejectedKeys) continue
                val score = scoreOf(a, b)
                val shared = sharedTerms(a, b)
                if (score < config.similarityThreshold || shared.isEmpty()) continue
                val strength = if (score >= config.strongScore) LinkStrength.STRONG else LinkStrength.MEDIUM
                out.add(LinkSuggestion(a.doc.id, b.doc.id, score, strength, shared))
            }
        }
        return out
            .sortedWith(
                compareByDescending<LinkSuggestion> { it.score }
                    .thenBy { minOf(it.firstId, it.secondId) }
                    .thenBy { maxOf(it.firstId, it.secondId) }
            )
            .take(config.maxSuggestions)
    }
}
