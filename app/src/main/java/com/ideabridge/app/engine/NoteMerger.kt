package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules

enum class MergeTemplate { PROBLEM_SOLUTION, CAUSE_EFFECT, MERGED_CHECKLIST, COMBINED }

/** اقتراح ملاحظة جديدة ناتجة عن دمج ملاحظتين. لا يُحفظ شيء إلا بموافقة المستخدم. */
data class MergeProposal(val title: String, val body: String, val template: MergeTemplate, val basis: List<String>)

sealed interface MergeResult {
    data class Ok(val proposals: List<MergeProposal>) : MergeResult
    data class NeedMore(val reason: String) : MergeResult
}

/**
 * يقترح دمج ملاحظتين بقوالب واضحة (لا توليد عشوائي): قائمة موحدة، مشكلة وحل، سبب ونتيجة، أو جمع عام.
 * القوالب هيكل مساعد للتفكير، وليست نتيجة مؤكدة.
 */
object NoteMerger {
    const val DISCLAIMER = "هذا اقتراح توليدي يحتاج إلى تقييمك، وليس نتيجة مؤكدة."
    private val MARKERS = listOf(ListEditing.UNCHECKED, ListEditing.CHECKED, ListEditing.BULLET)

    /**
     * @param first/second بترتيب الدور إن كان النوع PROBLEM_SOLUTION (المشكلة أولًا) أو CAUSE_EFFECT (السبب أولًا).
     * @param shared الكلمات المشتركة المعروفة من المحرك (لإظهارها في القالب العام).
     */
    fun propose(
        first: IdeaDoc,
        second: IdeaDoc,
        type: SuggestionType = SuggestionType.SIMILARITY,
        shared: List<String> = emptyList(),
        cues: List<String> = emptyList(),
    ): MergeResult {
        if (first.id == second.id) return MergeResult.NeedMore("اختر ملاحظتين مختلفتين.")
        val aBody = first.description.orEmpty().trim()
        val bBody = second.description.orEmpty().trim()
        if (first.title.isBlank() || second.title.isBlank()) return MergeResult.NeedMore("إحدى الملاحظتين بلا محتوى كافٍ.")

        val proposals = ArrayList<MergeProposal>()
        when (type) {
            SuggestionType.PROBLEM_SOLUTION -> proposals.add(problemSolution(first, second, aBody, bBody, cues))
            SuggestionType.CAUSE_EFFECT -> proposals.add(causeEffect(first, second, aBody, bBody, cues))
            SuggestionType.SIMILARITY -> Unit
        }
        mergedChecklist(first, second, aBody, bBody)?.let { proposals.add(it) }
        proposals.add(combined(first, second, aBody, bBody, shared))
        return MergeResult.Ok(proposals)
    }

    private fun problemSolution(a: IdeaDoc, b: IdeaDoc, aBody: String, bBody: String, cues: List<String>): MergeProposal {
        val body = buildString {
            append("المشكلة: ").append(a.title.trim()).append('\n')
            if (aBody.isNotEmpty()) append(aBody).append('\n')
            append("\nالحل المحتمل: ").append(b.title.trim()).append('\n')
            if (bBody.isNotEmpty()) append(bBody).append('\n')
            append("\nخطوات للتجربة:\n")
            append("☐ تحديد حجم المشكلة وقياسها قبل أي تغيير\n")
            append("☐ تجربة الحل على نطاق صغير\n")
            append("☐ مقارنة النتيجة بالقياس الأول")
        }
        return MergeProposal(
            title = limitTitle("حل «${a.title.trim()}» عبر «${b.title.trim()}»"),
            body = body,
            template = MergeTemplate.PROBLEM_SOLUTION,
            basis = listOf("إشارات صريحة في النص: " + cues.distinct().joinToString("، ") { "«$it»" }),
        )
    }

    private fun causeEffect(a: IdeaDoc, b: IdeaDoc, aBody: String, bBody: String, cues: List<String>): MergeProposal {
        val body = buildString {
            append("السبب المحتمل: ").append(a.title.trim()).append('\n')
            if (aBody.isNotEmpty()) append(aBody).append('\n')
            append("\nالنتيجة: ").append(b.title.trim()).append('\n')
            if (bBody.isNotEmpty()) append(bBody).append('\n')
            append("\nما يلزم التحقق منه:\n")
            append("☐ هل العلاقة سببية فعلًا أم مجرد تزامن؟\n")
            append("☐ ما الدليل الذي يدعمها؟")
        }
        return MergeProposal(
            title = limitTitle("علاقة «${a.title.trim()}» بـ«${b.title.trim()}»"),
            body = body,
            template = MergeTemplate.CAUSE_EFFECT,
            basis = listOf("إشارات صريحة في النص: " + cues.distinct().joinToString("، ") { "«$it»" }),
        )
    }

    /** يوحّد بنود قائمتين: يحذف المكرر (بعد توحيد الكتابة) ويحتفظ بحالة «منجز» إن وُجدت في أي منهما. */
    private fun mergedChecklist(a: IdeaDoc, b: IdeaDoc, aBody: String, bBody: String): MergeProposal? {
        val itemsA = listItems(aBody)
        val itemsB = listItems(bBody)
        if (itemsA.isEmpty() || itemsB.isEmpty()) return null
        val order = ArrayList<String>()
        val byKey = HashMap<String, Pair<String, String>>() // مفتاح المقارنة → (العلامة، النص)
        var duplicates = 0
        for ((marker, text) in itemsA + itemsB) {
            val key = ArabicText.normalizeChars(text).trim()
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = marker to text
                order.add(key)
            } else {
                duplicates++
                if (marker == ListEditing.CHECKED && existing.first != ListEditing.BULLET) {
                    byKey[key] = ListEditing.CHECKED to existing.second
                }
            }
        }
        val lines = order.map { key -> byKey.getValue(key).let { it.first + it.second } }
        return MergeProposal(
            title = limitTitle("قائمة موحدة: «${a.title.trim()}» + «${b.title.trim()}»"),
            body = lines.joinToString("\n"),
            template = MergeTemplate.MERGED_CHECKLIST,
            basis = listOf("${lines.size} بندًا، وتم حذف $duplicates بندًا مكررًا"),
        )
    }

    private fun combined(a: IdeaDoc, b: IdeaDoc, aBody: String, bBody: String, shared: List<String>): MergeProposal {
        val body = buildString {
            if (shared.isNotEmpty()) {
                append("ما يجمعهما: ").append(shared.distinct().take(5).joinToString("، ") { "«$it»" }).append("\n\n")
            }
            append("— من «").append(a.title.trim()).append("»:\n").append(aBody.ifEmpty { a.title.trim() }).append("\n\n")
            append("— من «").append(b.title.trim()).append("»:\n").append(bBody.ifEmpty { b.title.trim() }).append("\n\n")
            append("أسئلة للمتابعة:\n")
            append("☐ كيف تخدم «").append(a.title.trim()).append("» الهدف من «").append(b.title.trim()).append("»؟\n")
            append("☐ ما الخطوة الأولى لجمعهما عمليًا؟")
        }
        return MergeProposal(
            title = limitTitle("«${a.title.trim()}» و«${b.title.trim()}» معًا"),
            body = body,
            template = MergeTemplate.COMBINED,
            basis = listOf(
                if (shared.isEmpty()) "لا كلمات مشتركة معروفة" else "كلمات مشتركة: " + shared.distinct().take(5).joinToString("، ") { "«$it»" },
            ),
        )
    }

    private fun listItems(body: String): List<Pair<String, String>> =
        body.lines().mapNotNull { line ->
            val marker = MARKERS.firstOrNull { line.startsWith(it) } ?: return@mapNotNull null
            val text = line.removePrefix(marker).trim()
            if (text.isEmpty()) null else marker to text
        }

    private fun limitTitle(t: String): String =
        if (t.length <= IdeaRules.MAX_LENGTH) t else t.take(IdeaRules.MAX_LENGTH - 1) + "…"
}
