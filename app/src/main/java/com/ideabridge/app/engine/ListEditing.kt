package com.ideabridge.app.engine

/**
 * تحرير القوائم داخل نص الملاحظة العادي: قائمة مهام (☐ / ☑) ونقاط (•).
 * النص يبقى نصًا عاديًا، فيُبحث فيه ويُصدَّر ويُستورد كما هو. دوال بحتة بلا اعتماد على Android.
 */
object ListEditing {
    const val UNCHECKED = "☐ "
    const val CHECKED = "☑ "
    const val BULLET = "• "
    private val MARKERS = listOf(UNCHECKED, CHECKED, BULLET)

    data class Edit(val text: String, val selStart: Int, val selEnd: Int)

    private fun lineStart(text: String, index: Int): Int = text.lastIndexOf('\n', index - 1) + 1

    private fun lineEnd(text: String, index: Int): Int {
        val i = text.indexOf('\n', index)
        return if (i < 0) text.length else i
    }

    /** يزيل علامة القائمة من بداية السطر إن وُجدت (ويقبل العلامة وحدها بلا مسافة). */
    fun stripMarker(line: String): String {
        for (m in MARKERS) {
            if (line.startsWith(m)) return line.substring(m.length)
            if (line == m.trim()) return ""
        }
        return line
    }

    /** يحوّل الأسطر المحددة إلى قائمة مهام، أو يزيلها إن كانت كلها كذلك. */
    fun toggleChecklist(text: String, selStart: Int, selEnd: Int): Edit =
        togglePrefix(text, selStart, selEnd, listOf(UNCHECKED, CHECKED), UNCHECKED)

    /** يحوّل الأسطر المحددة إلى نقاط، أو يزيلها إن كانت كلها كذلك. */
    fun toggleBullet(text: String, selStart: Int, selEnd: Int): Edit =
        togglePrefix(text, selStart, selEnd, listOf(BULLET), BULLET)

    private fun togglePrefix(text: String, selStart: Int, selEnd: Int, own: List<String>, add: String): Edit {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        var end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        if (end > start && text[end - 1] == '\n') end -= 1
        val from = lineStart(text, start)
        val to = lineEnd(text, end)
        val lines = text.substring(from, to).split('\n')
        val nonBlank = lines.filter { it.isNotBlank() }
        val removing = nonBlank.isNotEmpty() && nonBlank.all { line -> own.any { line.startsWith(it) } }
        val result = lines.map { line ->
            when {
                removing -> own.firstOrNull { line.startsWith(it) }?.let { line.removePrefix(it) } ?: line
                line.isBlank() && lines.size > 1 -> line // الأسطر الفارغة بين أسطر محددة تُترك
                else -> add + stripMarker(line)
            }
        }
        val block = result.joinToString("\n")
        val cursor = from + block.length
        return Edit(text.substring(0, from) + block + text.substring(to), cursor, cursor)
    }

    /** يبدّل ☐ ↔ ☑ في السطر الذي فيه المؤشر؛ لا يغيّر شيئًا إن لم يكن السطر مهمة. */
    fun toggleDone(text: String, cursor: Int): Edit {
        val c = cursor.coerceIn(0, text.length)
        val from = lineStart(text, c)
        val line = text.substring(from, lineEnd(text, c))
        val swapped = when {
            line.startsWith(UNCHECKED) -> CHECKED + line.removePrefix(UNCHECKED)
            line.startsWith(CHECKED) -> UNCHECKED + line.removePrefix(CHECKED)
            else -> return Edit(text, c, c)
        }
        return Edit(text.substring(0, from) + swapped + text.substring(from + line.length), c, c)
    }

    /**
     * إن كان التغيير الأخير ضغطة Enter داخل عنصر قائمة: يبدأ عنصرًا جديدًا بعلامته (☑ تصبح ☐)،
     * وإن كان العنصر فارغًا يُنهي القائمة. وإلا يعيد null فيُترك التعديل كما كتبه المستخدم.
     */
    fun continueList(oldText: String, newText: String, newCursor: Int): Edit? {
        if (newText.length != oldText.length + 1) return null
        val pos = newCursor - 1
        if (pos < 0 || pos >= newText.length || newText[pos] != '\n') return null
        if (newText.removeRange(pos, pos + 1) != oldText) return null
        val start = lineStart(newText, pos)
        val line = newText.substring(start, pos)
        val marker = MARKERS.firstOrNull { line.startsWith(it) } ?: return null
        if (line.removePrefix(marker).isBlank()) {
            return Edit(newText.substring(0, start) + newText.substring(pos + 1), start, start)
        }
        val next = if (marker == BULLET) BULLET else UNCHECKED
        val cursor = newCursor + next.length
        return Edit(newText.substring(0, newCursor) + next + newText.substring(newCursor), cursor, cursor)
    }
}
