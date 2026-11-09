package com.ideabridge.app

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ideabridge.app.engine.Incoming
import com.ideabridge.app.engine.IncomingParser
import com.ideabridge.app.ui.AppLockState
import com.ideabridge.app.ui.IdeaBridgeApp
import com.ideabridge.app.ui.LockScreen
import com.ideabridge.app.ui.theme.IdeaBridgeTheme

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Incoming?>(null)
    private lateinit var lock: AppLockState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lock = AppLockState(getSharedPreferences(AppLockState.PREFS_NAME, Context.MODE_PRIVATE))
        // لا نعالج الـ Intent نفسه مرة ثانية بعد تدوير الشاشة (يمنع إنشاء ملاحظة مكررة)
        if (savedInstanceState == null) incoming = parse(intent)
        setContent {
            IdeaBridgeTheme {
                Box {
                    IdeaBridgeApp(incoming = incoming, onIncomingConsumed = { incoming = null }, lock = lock)
                    // القفل غطاء فوق التطبيق (لا يُهدم التطبيق تحته) فتبقى مسودتك وموضعك كما هما بعد الفتح
                    if (lock.locked) LockScreen(lock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForegrounded(SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        lock.onBackgrounded(SystemClock.elapsedRealtime())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = parse(intent)
    }

    private fun parse(intent: Intent?): Incoming? = IncomingParser.parse(
        action = intent?.action,
        mimeType = intent?.type,
        subject = intent?.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
    )
}
