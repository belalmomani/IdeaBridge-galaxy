package com.ideabridge.app.engine

/**
 * مطابقة بحث متسامحة مع اختلافات الكتابة العربية: التشكيل والتطويل وأشكال الألف والياء والهمزة،
 * والتاء المربوطة والهاء، و«ال» التعريف، والكلمات الشائعة (في، من...). كل كلمات البحث يجب أن توجد.
 * المطابقة على جزء من الكلمة (مثل البحث السابق): «طبخ» تجد «مطبخ».
 */
object SearchMatcher {
    fun normalize(text: String): String =
        ArabicText.normalizeChars(text).replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')

    fun queryTokens(query: String): List<String> {
        val raw = ArabicText.splitWords(query)
        val meaningful = raw.filter { ArabicText.normalizeChars(it) !in ArabicText.DEFAULT_STOP_WORDS }
        val source = if (meaningful.isEmpty()) raw else meaningful // بحث كله كلمات شائعة: نستعملها كما هي
        return source.map { ArabicText.stripPrefix(normalize(it)) }.filter { it.isNotEmpty() }
    }

    fun matches(query: String, vararg fields: String?): Boolean {
        val tokens = queryTokens(query)
        if (tokens.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ") { normalize(it) }
        return tokens.all { haystack.contains(it) }
    }
}
