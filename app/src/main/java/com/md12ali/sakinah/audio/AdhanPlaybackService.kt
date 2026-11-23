package com.md12ali.sakinah.audio

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.md12ali.sakinah.R
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.alarm.AdhanLog
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.ui.AdhanActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Foreground media service that plays the Adhan with the screen locked.
 *  1. startForeground() FIRST (Android kills the app if this is late)
 *  2. full-screen Adhan screen over the lock screen (like an alarm clock) with a big Stop button
 *  3. real Adhan recording (chosen file → downloaded Wikimedia recording → phone alarm tone)
 *  4. alarm audio stream, gentle fade-in, stops on call / Stop / end of recording
 * Every step is written to AdhanLog for the in-app health check.
 */
class AdhanPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAll(); return START_NOT_STICKY }

        val prayer = intent?.getStringExtra(AdhanScheduler.EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() } ?: Prayer.DHUHR
        val time = intent?.getStringExtra(AdhanScheduler.EXTRA_TIME).orEmpty()
        val place = intent?.getStringExtra(AdhanScheduler.EXTRA_PLACE).orEmpty()
        val mode = intent?.getStringExtra(AdhanScheduler.EXTRA_MODE)?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() }
        val preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
        val previewFile = intent?.getStringExtra(EXTRA_FILE)

        // 1) Foreground immediately.
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(prayer, time, place, fullScreen = !preview),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
        } catch (e: Exception) {
            AdhanLog.add(this, "startForeground failed: ${e.javaClass.simpleName} – used alarm notification")
            Notifications.showFallbackAlarm(this, prayer, time, place)
            stopSelf()
            return START_NOT_STICKY
        }
        _playing.value = Playing(prayer, time, place)

        // 2) Lock-screen Adhan screen (also launched by the notification's full-screen intent).
        if (!preview) runCatching {
            startActivity(AdhanActivity.intent(this, prayer, time, place).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // 3) Which recording?
        val settings = runCatching { runBlocking { (application as SakinahApp).container.settings.current() } }.getOrNull()
        val soft = mode == AlertMode.SOFT_ADHAN || (mode == null && settings?.alerts?.get(prayer) == AlertMode.SOFT_ADHAN)
        val isFajr = prayer == Prayer.FAJR
        val setting = previewFile ?: if (isFajr || soft) settings?.fajrSound.orEmpty() else settings?.mainSound.orEmpty()
        val source = AdhanAudio.resolve(this, setting, isFajr)
        val alarmStream = (settings?.useAlarmVolume ?: true) && !preview
        play(source, alarmStream, targetVolume = if (soft || isFajr) 0.7f else 1.0f)
        AdhanLog.add(this, "Playing ${prayer.english}: ${source?.substringAfterLast('/') ?: "phone alarm tone (no Adhan downloaded yet)"}")
        return START_NOT_STICKY
    }

    private fun play(source: String?, alarmStream: Boolean, targetVolume: Float) {
        stopPlayer()
        val attrs = AudioAttributes.Builder()
            .setUsage(if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val am = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) stopAll() }
            .build()
        runCatching { am.requestAudioFocus(focusRequest!!) }

        val mp = MediaPlayer()
        val ok = runCatching {
            when {
                source == null -> error("no recording")
                source.startsWith("asset:") -> assets.openFd(source.removePrefix("asset:")).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                else -> mp.setDataSource(source)
            }
        }.isSuccess
        if (!ok) {
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            runCatching { mp.reset(); mp.setDataSource(this, fallback) }
        }
        mp.setAudioAttributes(attrs)
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        mp.setVolume(0.15f, 0.15f)
        mp.setOnPreparedListener { it.start(); fadeIn(it, targetVolume) }
        mp.setOnCompletionListener { stopAll() }
        mp.setOnErrorListener { _, what, extra ->
            AdhanLog.add(this, "Player error $what/$extra – used alarm notification")
            Notifications.showFallbackAlarm(this, _playing.value?.prayer ?: Prayer.DHUHR, _playing.value?.time.orEmpty(), _playing.value?.place.orEmpty())
            stopAll(); true
        }
        runCatching { mp.prepareAsync() }.onFailure {
            AdhanLog.add(this, "Could not prepare audio: ${it.javaClass.simpleName}")
            Notifications.showFallbackAlarm(this, _playing.value?.prayer ?: Prayer.DHUHR, _playing.value?.time.orEmpty(), _playing.value?.place.orEmpty())
            stopAll(); return
        }
        player = mp
        handler.postDelayed({ stopAll() }, 8 * 60_000L) // safety net
    }

    private fun fadeIn(mp: MediaPlayer, target: Float, steps: Int = 20, totalMs: Long = 4_000) {
        for (i in 1..steps) {
            handler.postDelayed({
                runCatching { val v = 0.15f + (target - 0.15f) * i / steps; mp.setVolume(v, v) }
            }, totalMs * i / steps)
        }
    }

    private fun buildNotification(prayer: Prayer, time: String, place: String, fullScreen: Boolean) =
        NotificationCompat.Builder(this, Notifications.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${prayer.english} · ${prayer.arabic}")
            .setContentText(listOf(time, place).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Adhan" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(activityIntent(prayer, time, place))
            .apply { if (fullScreen) setFullScreenIntent(activityIntent(prayer, time, place), true) }
            .addAction(0, "Stop Adhan", stopIntent(this))
            .setDeleteIntent(stopIntent(this))
            .build()

    private fun activityIntent(prayer: Prayer, time: String, place: String): PendingIntent =
        PendingIntent.getActivity(
            this, 11, AdhanActivity.intent(this, prayer, time, place),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun stopPlayer() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { if (isPlaying) stop(); release() }
        player = null
        focusRequest?.let { runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private fun stopAll() {
        stopPlayer()
        _playing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopPlayer(); _playing.value = null; super.onDestroy() }

    data class Playing(val prayer: Prayer, val time: String, val place: String)

    companion object {
        const val ACTION_STOP = "com.md12ali.sakinah.STOP_ADHAN"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_FILE = "file"
        private const val NOTIFICATION_ID = 1001

        private val _playing = MutableStateFlow<Playing?>(null)
        /** Observed by the lock-screen Adhan screen so it closes when the Adhan ends. */
        val playing: StateFlow<Playing?> = _playing

        fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
            context, 1, Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** Settings → Preview / Play now (app is open, so starting the service is always allowed). */
        fun preview(context: Context, prayer: Prayer, file: String) {
            val i = Intent(context, AdhanPlaybackService::class.java)
                .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer.name)
                .putExtra(EXTRA_PREVIEW, true)
                .putExtra(EXTRA_FILE, file)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP)) }
        }

        /** Choices for the sound picker: "" = automatic, downloaded recordings, the user's own files, bundled assets. */
        fun availableSounds(context: Context): List<String> {
            val ok = { n: String -> n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".m4a") || n.endsWith(".wav") }
            val builtIn = AdhanAudio.downloaded(context).map { it.second.absolutePath }
            val bundled = context.assets.list("adhan").orEmpty().filter(ok).map { "adhan/$it" }
            val chosen = userSoundsDir(context).listFiles().orEmpty().filter { ok(it.name.lowercase()) }.map { it.absolutePath }
            return builtIn + chosen + bundled
        }

        /** Friendly name for a sound path. */
        fun label(context: Context, path: String): String {
            if (path.isBlank()) return "Automatic (best real Adhan)"
            AdhanAudio.catalog.firstOrNull { AdhanAudio.fileFor(context, it).absolutePath == path }?.let { return it.title }
            return File(path).name
        }

        fun userSoundsDir(context: Context) = File(context.filesDir, "adhan").apply { mkdirs() }

        /** Copies an audio file the user picked (Files / Downloads / WhatsApp…) into app storage so alarms can always read it. */
        fun importSound(context: Context, uri: android.net.Uri): String? = runCatching {
            var name = "adhan_${System.currentTimeMillis()}.mp3"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i)?.let { n -> name = n.replace(Regex("[^A-Za-z0-9._ -]"), "_") }
            }
            if (!name.contains('.')) name += ".mp3"
            val out = File(userSoundsDir(context), name)
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.absolutePath
        }.getOrNull()
    }
}
