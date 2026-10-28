package com.ideabridge.app.engine

/**
 * معالجة محافظة للنص العربي لأغراض المقارنة فقط (النص الأصلي لا يتغير أبدًا).
 * ما يُفعل: إزالة التشكيل والتطويل، توحيد الألف والياء، توحيد الأرقام، إزالة "ال" التعريف وما يلحق بها
 * (فقط إذا بقي ثلاثة أحرف أو أكثر)، وتجاهل الكلمات الشائعة وعلامات الترقيم.
 * ما لا يُفعل عمدًا: لا استخراج للجذور ولا إزالة للجموع أو التاء المربوطة، حتى لا تختلط كلمات مختلفة المعنى.
 */
object ArabicText {
    data class Token(val norm: String, val original: String)

    /** كلمات عامة شائعة (مكتوبة بصيغتها الموحدة). قابلة للتعديل عبر EngineConfig. */
    val DEFAULT_STOP_WORDS: Set<String> = setOf(
        "من", "في", "علي", "الي", "عن", "هذا", "هذه", "ذلك", "تلك", "هو", "هي", "هم", "هن", "انا", "نحن", "انت",
        "او", "ثم", "مع", "كل", "ان", "انه", "انها", "التي", "الذي", "الذين", "بعد", "قبل", "بين", "عند",
        "لا", "ما", "يا", "كان", "كانت", "يكون", "كيف", "ماذا", "لو", "هل", "قد", "لم", "لن", "اذا", "حتي",
        "ايضا", "فقط", "اي", "بعض", "غير", "نفس", "عليه", "عليها", "منه", "منها", "فيه", "فيها", "به", "بها",
        "له", "لها", "لكن", "بل", "كما", "لان", "حيث", "مثل", "هنا", "هناك", "ضمن", "خلال", "عبر", "لدي",
    )

    private val PREFIXES = listOf("وال", "بال", "ال", "لل")

    fun normalizeChars(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            when {
                code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 -> Unit // تشكيل وتطويل
                ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
                ch == 'ى' -> sb.append('ي')
                code in 0x0660..0x0669 -> sb.append('0' + (code - 0x0660))
                code in 0x06F0..0x06F9 -> sb.append('0' + (code - 0x06F0))
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }

    private fun isWordChar(ch: Char): Boolean {
        val code = ch.code
        return Character.isLetterOrDigit(ch) || code in 0x064B..0x065F || code == 0x0670 || code == 0x0640
    }

    /** يقسم النص الأصلي إلى كلمات بلا علامات ترقيم (يبقي التشكيل جزءًا من الكلمة). */
    fun splitWords(text: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        for (ch in text) {
            if (isWordChar(ch)) current.append(ch) else if (current.isNotEmpty()) {
                words.add(current.toString()); current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    fun stripPrefix(word: String): String {
        for (p in PREFIXES) {
            if (word.startsWith(p) && word.length - p.length >= 3) return word.substring(p.length)
        }
        return word
    }

    fun tokens(text: String, stopWords: Set<String> = DEFAULT_STOP_WORDS): List<Token> {
        val out = ArrayList<Token>()
        for (raw in splitWords(text)) {
            val norm = normalizeChars(raw)
            if (norm.length < 2 || norm in stopWords) continue
            val stem = stripPrefix(norm)
            if (stem.length < 2 || stem in stopWords) continue
            out.add(Token(stem, raw))
        }
        return out
    }
}
