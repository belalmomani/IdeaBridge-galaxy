package com.ideabridge.app.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ideabridge.app.R
import com.ideabridge.app.engine.LockPolicy

/**
 * حالة قفل التطبيق. المفتاح الوحيد المحفوظ هو «مفعّل أم لا» (ليس سرًا).
 * عند تشغيل جديد يبدأ مقفلًا إن كان مفعّلًا، ويُعاد قفله بعد غياب 30 ثانية في الخلفية.
 */
class AppLockState(private val prefs: SharedPreferences) {
    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, false))
        private set
    var locked by mutableStateOf(enabled)
        private set
    private var backgroundedAt = 0L

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        locked = false
        backgroundedAt = 0L
    }

    fun unlock() {
        locked = false
        backgroundedAt = 0L
    }

    fun onBackgrounded(now: Long) { backgroundedAt = now }

    fun onForegrounded(now: Long) {
        if (LockPolicy.shouldRelock(enabled, backgroundedAt, now)) locked = true
    }

    companion object {
        const val PREFS_NAME = "ideabridge_settings"
        private const val KEY_ENABLED = "app_lock_enabled"
    }
}

fun deviceHasSecureLock(context: Context): Boolean =
    (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

/**
 * يعيد دالة تفتح نافذة تأكيد قفل الجهاز الرسمية في النظام (رمز/نمط/كلمة مرور/بصمة بحسب الهاتف).
 * إن لم يكن للهاتف قفل أصلًا تُستدعى onNotAvailable بدل أن تُحبس المستخدم.
 */
@Composable
fun rememberConfirmDeviceCredential(
    title: String,
    description: String,
    onNotAvailable: () -> Unit,
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onResult(result.resultCode == Activity.RESULT_OK)
    }
    return {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, description)
        if (intent == null) onNotAvailable() else launcher.launch(intent)
    }
}

/** شاشة القفل: تغطي التطبيق بالكامل وتمتص اللمس، وتفتح نافذة التأكيد تلقائيًا. */
@Composable
fun LockScreen(lock: AppLockState) {
    val confirm = rememberConfirmDeviceCredential(
        title = stringResource(R.string.lock_prompt_title),
        description = stringResource(R.string.lock_prompt_desc),
        onNotAvailable = { lock.unlock() }, // لا قفل للجهاز: لا يمكن حماية التطبيق، فيُفتح بدل أن يُحبس المستخدم
        onResult = { ok -> if (ok) lock.unlock() },
    )
    LaunchedEffect(Unit) { confirm() }
    SpaceBackground(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { confirm() }) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
