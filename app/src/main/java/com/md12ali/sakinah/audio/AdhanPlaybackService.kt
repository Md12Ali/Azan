package com.md12ali.sakinah.audio

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
import com.md12ali.sakinah.R
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import kotlinx.coroutines.runBlocking

/**
 * Foreground media service that plays the Adhan smoothly with the screen locked.
 *  - Gentle 5-second fade-in (calm, never startling)
 *  - Plays on the ALARM stream by default so it respects alarm volume, not media volume
 *  - "Stop" action in the notification; also stops if a call takes audio focus
 *  - Falls back to the phone's alarm tone if no Adhan file is bundled
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
        val preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
        val previewFile = intent?.getStringExtra(EXTRA_FILE)

        // Must call startForeground within a few seconds of startForegroundService().
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(prayer, time, place),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        val settings = runBlocking { (application as SakinahApp).container.settings.current() }
        val soft = settings.alerts[prayer] == AlertMode.SOFT_ADHAN
        val file = previewFile ?: if (prayer == Prayer.FAJR || soft) settings.fajrSound else settings.mainSound
        play(file, settings.useAlarmVolume && !preview, targetVolume = if (soft) 0.55f else 0.9f)
        return START_NOT_STICKY
    }

    private fun play(assetPath: String, alarmStream: Boolean, targetVolume: Float) {
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
        am.requestAudioFocus(focusRequest!!)

        val mp = MediaPlayer()
        try {
            if (assetPath.startsWith("/")) mp.setDataSource(assetPath)            // recording chosen from the phone
            else assets.openFd(assetPath).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
        } catch (e: Exception) {
            // No Adhan file bundled yet → phone's default alarm sound so the alert still happens.
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mp.setDataSource(this, fallback)
        }
        mp.setAudioAttributes(attrs)
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        mp.setVolume(0f, 0f)
        mp.setOnPreparedListener { it.start(); fadeIn(it, targetVolume) }
        mp.setOnCompletionListener { stopAll() }
        mp.setOnErrorListener { _, _, _ -> stopAll(); true }
        mp.prepareAsync()
        player = mp
        // Safety net: never play longer than 6 minutes.
        handler.postDelayed({ stopAll() }, 6 * 60_000L)
    }

    private fun fadeIn(mp: MediaPlayer, target: Float, steps: Int = 25, totalMs: Long = 5_000) {
        for (i in 1..steps) {
            handler.postDelayed({
                runCatching { val v = target * i / steps; mp.setVolume(v, v) }
            }, totalMs * i / steps)
        }
    }

    private fun buildNotification(prayer: Prayer, time: String, place: String) =
        NotificationCompat.Builder(this, Notifications.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${prayer.english} · ${prayer.arabic}")
            .setContentText(listOf(time, place).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Adhan" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(Notifications.openAppIntent(this))
            .addAction(0, "Stop", stopIntent(this))
            .setDeleteIntent(stopIntent(this))
            .build()

    private fun stopPlayer() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { if (isPlaying) stop(); release() }
        player = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun stopAll() {
        stopPlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopPlayer(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.md12ali.sakinah.STOP_ADHAN"
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_FILE = "file"
        private const val NOTIFICATION_ID = 1001

        fun stopIntent(context: Context) = android.app.PendingIntent.getService(
            context, 1, Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** Used by Settings → "Preview" (app is in the foreground, so starting the service is allowed). */
        fun preview(context: Context, prayer: Prayer, file: String) {
            val i = Intent(context, AdhanPlaybackService::class.java)
                .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer.name)
                .putExtra(EXTRA_PREVIEW, true)
                .putExtra(EXTRA_FILE, file)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AdhanPlaybackService::class.java).setAction(ACTION_STOP))
        }

        /** Audio files available in assets/adhan (shown in the sound picker). */
        fun availableSounds(context: Context): List<String> {
            val ok = { n: String -> n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".m4a") || n.endsWith(".wav") }
            val bundled = context.assets.list("adhan").orEmpty().filter(ok).map { "adhan/$it" }
            val chosen = userSoundsDir(context).listFiles().orEmpty().filter { ok(it.name.lowercase()) }.map { it.absolutePath }
            return chosen + bundled
        }

        fun userSoundsDir(context: Context) = java.io.File(context.filesDir, "adhan").apply { mkdirs() }

        /** Copies an audio file the user picked (Files / Downloads / WhatsApp…) into app storage so alarms can always read it. */
        fun importSound(context: Context, uri: android.net.Uri): String? = runCatching {
            var name = "adhan_${System.currentTimeMillis()}.mp3"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i)?.let { n -> name = n.replace(Regex("[^A-Za-z0-9._ -]"), "_") }
            }
            if (!name.contains('.')) name += ".mp3"
            val out = java.io.File(userSoundsDir(context), name)
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.absolutePath
        }.getOrNull()
    }
}
