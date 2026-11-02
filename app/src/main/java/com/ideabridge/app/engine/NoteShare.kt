package com.ideabridge.app.engine

/** يبني نص المشاركة من الملاحظة: العنوان ثم النص، دون تكرار العنوان إن كان هو أول سطر في النص. */
object NoteShare {
    fun format(title: String, body: String): String {
        val t = title.trim()
        val b = body.trim()
        return when {
            b.isEmpty() -> t
            t.isEmpty() -> b
            b.startsWith(t) -> b
            else -> t + "\n\n" + b
        }
    }
}
