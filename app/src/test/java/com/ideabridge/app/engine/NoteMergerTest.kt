package com.ideabridge.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMergerTest {
    private fun doc(id: Long, title: String, body: String? = null) = IdeaDoc(id, title, body)
    private fun ok(r: MergeResult) = (r as MergeResult.Ok).proposals

    @Test fun twoChecklistsAreMergedWithoutDuplicatesAndKeepDoneState() {
        val a = doc(1, "مشتريات الصباح", "☐ حليب\n☐ خبز")
        val b = doc(2, "مشتريات المساء", "☑ خبز\n☐ بيض\n• جبن")
        val merged = ok(NoteMerger.propose(a, b)).first { it.template == MergeTemplate.MERGED_CHECKLIST }
        assertEquals("☐ حليب\n☑ خبز\n☐ بيض\n• جبن", merged.body)
        assertTrue(merged.basis.single().contains("حذف 1"))
    }

    @Test fun problemSolutionTemplateComesFirstAndUsesTheRealTitlesAndCues() {
        val problem = doc(1, "تقليل هدر الأعلاف", "نخسر كثيرًا كل أسبوع")
        val solution = doc(2, "تطبيق لمتابعة الأعلاف")
        val first = ok(NoteMerger.propose(problem, solution, SuggestionType.PROBLEM_SOLUTION, emptyList(), listOf("هدر", "تطبيق"))).first()
        assertEquals(MergeTemplate.PROBLEM_SOLUTION, first.template)
        assertTrue(first.title.contains("تقليل هدر الأعلاف") && first.title.contains("تطبيق لمتابعة الأعلاف"))
        assertTrue(first.body.contains("نخسر كثيرًا كل أسبوع"))
        assertTrue(first.basis.single().contains("هدر"))
    }

    @Test fun causeEffectTemplateAsksForEvidenceInsteadOfClaimingCausation() {
        val first = ok(
            NoteMerger.propose(doc(1, "الحرارة العالية"), doc(2, "نفوق الدجاج"), SuggestionType.CAUSE_EFFECT, emptyList(), listOf("تسبب"))
        ).first()
        assertEquals(MergeTemplate.CAUSE_EFFECT, first.template)
        assertTrue(first.body.contains("هل العلاقة سببية فعلًا"))
    }

    @Test fun theGeneralTemplateIsAlwaysAvailableAndShowsSharedWords() {
        val proposals = ok(NoteMerger.propose(doc(1, "تربية الدجاج"), doc(2, "تغذية الدجاج"), shared = listOf("الدجاج")))
        assertEquals(1, proposals.size)
        val p = proposals.single()
        assertEquals(MergeTemplate.COMBINED, p.template)
        assertTrue(p.body.contains("«الدجاج»") && p.body.contains("تربية الدجاج") && p.body.contains("تغذية الدجاج"))
    }

    @Test fun differentTypesGiveDifferentTextNotOneTemplateWithWordsSwapped() {
        val a = doc(1, "تقليل هدر الأعلاف")
        val b = doc(2, "تطبيق لمتابعة الأعلاف")
        val bodies = ok(NoteMerger.propose(a, b, SuggestionType.PROBLEM_SOLUTION, listOf("الأعلاف"), listOf("هدر"))).map { it.body }
        assertEquals(bodies.size, bodies.distinct().size)
        assertTrue(bodies.size >= 2)
    }

    @Test fun invalidInputGivesAnExplanationNotAFakeResult() {
        assertTrue(NoteMerger.propose(doc(1, "أ"), doc(1, "أ")) is MergeResult.NeedMore)
        assertTrue(NoteMerger.propose(doc(1, "  "), doc(2, "ب")) is MergeResult.NeedMore)
    }

    @Test fun titlesNeverExceedTheLimit() {
        val long = "ا".repeat(120)
        ok(NoteMerger.propose(doc(1, long), doc(2, long))).forEach { assertTrue(it.title.length <= 140) }
    }
}
