package com.ideabridge.app.engine

import kotlin.math.ln
import kotlin.math.sqrt

/** فكرة كما يراها المحرك: معرف وعنوان ووصف اختياري فقط (لا اعتماد على Android أو Room). */
data class IdeaDoc(val id: Long, val title: String, val description: String? = null)

data class EngineConfig(
    /** أدنى درجة تشابه لاعتبار زوج مرشحًا بلا قاعدة خاصة (0..100). */
    val minScore: Int = 20,
    /** من هذه الدرجة يُسمّى الاقتراح "تشابه". */
    val similarityThreshold: Int = 30,
    /** من هذه الدرجة يُوصف التشابه بأنه قوي. */
    val strongScore: Int = 60,
    /** من هذه الدرجة تُعتبر الفكرتان متطابقتين تقريبًا. */
    val duplicateScore: Int = 85,
    val maxSuggestions: Int = 30,
    val maxAnalyzedIdeas: Int = 300,
    /** كلمة تظهر في نسبة أكبر من هذه من الأفكار تُهمل في التشابه (تُطبَّق فقط عند وجود فكرتين أو أكثر من الحد الأدنى). */
    val maxDocFraction: Double = 0.6,
    val minDocsForFraction: Int = 5,
    val stopWords: Set<String> = ArabicText.DEFAULT_STOP_WORDS,
)

internal class Entry(
    val doc: IdeaDoc,
    /** كل كلمات الفكرة (موحدة → أصلية) */
    val terms: Map<String, String>,
    /** الكلمات المميزة بأوزانها (TF-IDF مبسط) */
    val weights: Map<String, Double>,
    val norm: Double,
) {
    val isThin: Boolean get() = weights.isEmpty()

    /** الكلمات الأصلية للفكرة التي تطابق قائمة إشارات. */
    fun matches(lexicon: Set<String>): List<String> =
        terms.filterKeys { Lexicon.cueKey(it) in lexicon }.values.toList()
}

/**
 * وزن الكلمة = (1 + ln(تكرارها)) × (ln((N+1)/(df+1)) + 1)
 * حيث N عدد الأفكار وdf عدد الأفكار التي تحتوي الكلمة. كلمات العنوان تُحسب مرتين.
 */
internal class KeywordIndex(docs: List<IdeaDoc>, config: EngineConfig) {
    val entries: List<Entry>

    init {
        val n = docs.size
        val tokenized = docs.map { d ->
            Triple(d, ArabicText.tokens(d.title, config.stopWords), ArabicText.tokens(d.description.orEmpty(), config.stopWords))
        }
        val df = HashMap<String, Int>()
        for ((_, t, d) in tokenized) {
            (t + d).map { it.norm }.toSet().forEach { df[it] = (df[it] ?: 0) + 1 }
        }
        entries = tokenized.map { (doc, t, d) ->
            val counts = LinkedHashMap<String, Int>()
            val terms = LinkedHashMap<String, String>()
            for (tok in t) {
                counts[tok.norm] = (counts[tok.norm] ?: 0) + 2
                if (!terms.containsKey(tok.norm)) terms[tok.norm] = tok.original
            }
            for (tok in d) {
                counts[tok.norm] = (counts[tok.norm] ?: 0) + 1
                if (!terms.containsKey(tok.norm)) terms[tok.norm] = tok.original
            }
            val weights = LinkedHashMap<String, Double>()
            for ((term, count) in counts) {
                val docFreq = df[term] ?: 1
                if (n >= config.minDocsForFraction && docFreq.toDouble() / n > config.maxDocFraction) continue
                val idf = ln((n + 1.0) / (docFreq + 1.0)) + 1.0
                weights[term] = (1.0 + ln(count.toDouble())) * idf
            }
            Entry(doc, terms, weights, sqrt(weights.values.sumOf { it * it }))
        }
    }
}

internal fun cosine(a: Entry, b: Entry): Double {
    if (a.norm == 0.0 || b.norm == 0.0) return 0.0
    var dot = 0.0
    for ((term, w) in a.weights) {
        val other = b.weights[term]
        if (other != null) dot += w * other
    }
    return dot / (a.norm * b.norm)
}

internal fun scoreOf(a: Entry, b: Entry): Int =
    if (a.doc.id == b.doc.id) 0 else Math.round(cosine(a, b) * 100).toInt().coerceIn(0, 100)

internal fun sharedTerms(a: Entry, b: Entry, limit: Int = 5): List<String> =
    a.weights.keys.filter { it in b.weights }
        .sortedByDescending { (a.weights[it] ?: 0.0) * (b.weights[it] ?: 0.0) }
        .take(limit)
        .map { a.terms[it] ?: it }
