package com.ideabridge.app.engine

import com.ideabridge.app.IdeaRules

/** ما قد يصل التطبيق من خارجه: نص مُشارَك من تطبيق آخر، أو طلب ملاحظة جديدة من اختصار الأيقونة. */
sealed interface Incoming {
    data class SharedText(val title: String, val body: String) : Incoming
    data object NewNote : Incoming
}

/** يحوّل بيانات Intent إلى Incoming دون الاعتماد على Android (فيُختبر على JVM). */
object IncomingParser {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_NEW_NOTE = "com.ideabridge.app.action.NEW_NOTE"

    fun parse(action: String?, mimeType: String?, subject: String?, text: String?): Incoming? = when {
        action == ACTION_NEW_NOTE -> Incoming.NewNote
        action == ACTION_SEND && mimeType?.startsWith("text/") == true && !text.isNullOrBlank() ->
            Incoming.SharedText(
                title = subject.orEmpty().trim().take(IdeaRules.MAX_LENGTH),
                body = text.trim().take(IdeaRules.MAX_DESCRIPTION),
            )
        else -> null
    }
}
