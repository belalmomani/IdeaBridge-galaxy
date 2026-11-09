package com.ideabridge.app.engine

/** متى يُعاد قفل التطبيق بعد أن غاب في الخلفية. منطق بحت قابل للاختبار على JVM. */
object LockPolicy {
    const val TIMEOUT_MS = 30_000L

    /**
     * @param backgroundedAt وقت ذهاب التطبيق إلى الخلفية (0 = لم يغب بعد، أي تشغيل جديد يُقفل من البداية وليس من هنا).
     */
    fun shouldRelock(enabled: Boolean, backgroundedAt: Long, now: Long, timeoutMs: Long = TIMEOUT_MS): Boolean =
        enabled && backgroundedAt > 0L && now - backgroundedAt >= timeoutMs
}
