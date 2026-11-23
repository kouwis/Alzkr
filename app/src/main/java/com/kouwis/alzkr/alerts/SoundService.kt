package com.kouwis.alzkr.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.AlertEvent
import com.kouwis.alzkr.data.Prayer
import com.kouwis.alzkr.data.SoundEntry
import com.kouwis.alzkr.data.Sounds
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

/**
 * Plays the adhan (once, to its end) or rings an alarm (on a loop, until stopped or snoozed),
 * as a foreground service so it is not cut off. Sounds go through the alarm stream, so the
 * alarm volume sets how loud they are.
 */
class SoundService : Service() {
    companion object {
        const val ACTION_PLAY = "com.kouwis.alzkr.PLAY"
        const val ACTION_ALARM = "com.kouwis.alzkr.ALARM"
        const val ACTION_STOP = "com.kouwis.alzkr.STOP"
        const val ACTION_SNOOZE = "com.kouwis.alzkr.SNOOZE"
        const val EXTRA_EVENT = "event"
        private const val ID_PLAY = 7001
        private const val ID_ALARM = 7002
        private const val ALARM_MAX_MS = 10 * 60_000L
        const val SNOOZE_MIN = 5

        /** The alarm now ringing (for the full-screen alarm page), or null. */
        val ringing = MutableStateFlow<AlertEvent?>(null)

        fun start(context: Context, action: String, e: AlertEvent) {
            val i = Intent(context, SoundService::class.java).setAction(action)
                .putExtra(EXTRA_EVENT, Json.encodeToString(AlertEvent.serializer(), e))
            ContextCompat.startForegroundService(context, i)
        }
    }

    private var player: MediaPlayer? = null
    private var current: AlertEvent? = null
    private var mode: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        com.kouwis.alzkr.data.CrashLog.install(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val e = intent?.getStringExtra(EXTRA_EVENT)?.let { runCatching { Json.decodeFromString(AlertEvent.serializer(), it) }.getOrNull() }
        when (intent?.action) {
            ACTION_PLAY -> if (e != null) play(e) else stopSelf()
            ACTION_ALARM -> if (e != null) ring(e) else stopSelf()
            ACTION_SNOOZE -> { snooze(); finish(keepNote = false) }
            else -> finish(keepNote = mode == ACTION_PLAY)
        }
        return START_NOT_STICKY
    }

    private fun foreground(id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(id, n)
    }

    private fun serviceIntent(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, SoundService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun play(e: AlertEvent) {
        release()
        current = e; mode = ACTION_PLAY
        Channels.ensure(this)
        val n = NotificationCompat.Builder(this, Channels.ADHAN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(e.title)
            .setContentText(e.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(AlertScheduler.openApp(this, e.open))
            .setDeleteIntent(serviceIntent(ACTION_STOP, 1))
            .addAction(0, t("إيقاف الأذان"), serviceIntent(ACTION_STOP, 2))
            .build()
        foreground(ID_PLAY, n)
        if (!startPlayer(e.sound, loop = false, prayer = e.prayer)) finish(keepNote = true)
    }

    private fun ring(e: AlertEvent) {
        release()
        current = e; mode = ACTION_ALARM
        Channels.ensure(this)
        val full = PendingIntent.getActivity(
            this, 9, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, Channels.ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(e.title)
            .setContentText(e.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, t("غفوة {0} دقائق", com.kouwis.alzkr.data.ArabicText.num(SNOOZE_MIN)), serviceIntent(ACTION_SNOOZE, 3))
            .addAction(0, t("إيقاف"), serviceIntent(ACTION_STOP, 4))
            .build()
        foreground(ID_ALARM, n)
        ringing.value = e
        if (!startPlayer(e.sound, loop = true, prayer = e.prayer)) startPlayer("alarm", loop = true, prayer = null)
        vibrate()
        handler.postDelayed({ finish(keepNote = false) }, ALARM_MAX_MS)
    }

    /** Plays a recording, falling back to the bundled adhan, then to the phone's own sound. */
    private fun startPlayer(sound: String, loop: Boolean, prayer: String?): Boolean {
        val catalog = Sounds.catalog(this)
        val p = MediaPlayer()
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        val ok = runCatching {
            if (!setSource(p, catalog, sound)) {
                val fallback = if (sound.startsWith("full:") || sound.startsWith("short:"))
                    Sounds.defaultFor(Prayer.entries.firstOrNull { it.name == prayer } ?: Prayer.Dhuhr, catalog)
                        .let { if (sound.startsWith("short:")) it.replace("full:", "short:") else it }
                else sound
                if (!setSource(p, catalog, fallback)) {
                    val type = if (sound == "alarm" || loop) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
                    p.setDataSource(this, RingtoneManager.getDefaultUri(type) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                }
            }
            p.isLooping = loop
            p.setOnCompletionListener { if (!loop) finish(keepNote = true) }
            p.prepare()
            p.start()
            true
        }.getOrDefault(false)
        if (ok) player = p else p.release()
        return ok
    }

    private fun setSource(p: MediaPlayer, catalog: List<SoundEntry>, sound: String): Boolean {
        if (sound == "alarm") { p.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)); return true }
        if (sound == "sys" || sound == "vib") { p.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)); return true }
        val name = Sounds.fileName(catalog, sound) ?: return false
        if (Sounds.isBundled(catalog, sound)) {
            assets.openFd("audio/$name").use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            return true
        }
        val f = Sounds.localFile(this, name)
        if (!f.exists()) return false
        p.setDataSource(f.path)
        return true
    }

    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1200), 0)) }
    }

    private fun snooze() {
        val e = current ?: return
        val at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        val am = getSystemService(AlarmManager::class.java)
        AlertScheduler.setAt(this, am, at, AlertScheduler.pending(this, AlertScheduler.RC_SNOOZE, listOf(e.copy(at = at))), clock = true)
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    /** Stops the sound; after an adhan a plain note of it stays in the notifications. */
    private fun finish(keepNote: Boolean) {
        val e = current
        val wasPlay = mode == ACTION_PLAY
        release()
        ringing.value = null
        current = null; mode = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (keepNote && wasPlay && e != null) AlertReceiver.notify(this, e, Channels.ADHAN)
        stopSelf()
    }

    override fun onDestroy() {
        release()
        ringing.value = null
        super.onDestroy()
    }
}
