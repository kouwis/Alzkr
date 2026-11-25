package com.kouwis.alzkr.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kouwis.alzkr.MainActivity
import com.kouwis.alzkr.R
import com.kouwis.alzkr.data.ArabicText
import com.kouwis.alzkr.data.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** What is being recited now (null: nothing). */
data class Recitation(val reciter: String, val s: Int, val a: Int, val playing: Boolean, val loading: Boolean)

/**
 * Listening to the Quran, verse by verse: each verse is fetched once (kept for listening again
 * without the internet), the next ones are fetched ahead, and the mushaf follows the verse heard.
 * The basmala comes before the first verse of each surah (but al-Fatiha, where it is the first
 * verse, and at-Tawba).
 */
object QuranAudio {
    val state = MutableStateFlow<Recitation?>(null)
    /** a message for the reader (no internet, …), shown once */
    val error = MutableStateFlow<String?>(null)

    private fun send(context: Context, action: String, s: Int = 0, a: Int = 0) {
        val i = Intent(context, QuranPlayerService::class.java).setAction(action).putExtra("s", s).putExtra("a", a)
        runCatching {
            if (action == QuranPlayerService.PLAY) androidx.core.content.ContextCompat.startForegroundService(context, i)
            else context.startService(i)
        }
    }

    fun play(context: Context, s: Int, a: Int) = send(context, QuranPlayerService.PLAY, s, a)
    fun toggle(context: Context) = send(context, QuranPlayerService.TOGGLE)
    fun next(context: Context) = send(context, QuranPlayerService.NEXT)
    fun previous(context: Context) = send(context, QuranPlayerService.PREV)
    fun stop(context: Context) = send(context, QuranPlayerService.STOP)

    /** Another reciter: chosen for good, and the verse playing now starts again in his voice. */
    fun choose(context: Context, id: String) {
        Reciters.choose(context, id)
        state.value?.let { play(context, it.s, it.a) }
    }

    fun file(context: Context, r: Reciter, s: Int, a: Int) =
        File(context.filesDir, "recitations/${r.folder}/%03d%03d.mp3".format(s, a))

    /** The space the kept recordings take, in MB. */
    fun storedMb(context: Context): Long =
        (File(context.filesDir, "recitations").walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024))

    fun clearStored(context: Context) { File(context.filesDir, "recitations").deleteRecursively() }
}

class QuranPlayerService : Service() {
    companion object {
        const val PLAY = "com.kouwis.alzkr.quran.PLAY"
        const val TOGGLE = "com.kouwis.alzkr.quran.TOGGLE"
        const val NEXT = "com.kouwis.alzkr.quran.NEXT"
        const val PREV = "com.kouwis.alzkr.quran.PREV"
        const val STOP = "com.kouwis.alzkr.quran.STOP"
        private const val CHANNEL = "quran_audio"
        private const val ID = 7401
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var reciter = Reciters.all.first()
    private var s = 1
    private var a = 1
    private var paused = false
    private var focus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                reciter = Reciters.current(this)
                s = intent.getIntExtra("s", 1).coerceIn(1, 114)
                a = intent.getIntExtra("a", 1).coerceIn(1, Reciters.ayahs[s - 1])
                paused = false
                foreground()
                start()
            }
            TOGGLE -> if (QuranAudio.state.value == null) stopSelf() else { if (paused) resume() else pause() }
            NEXT -> skip(+1)
            PREV -> skip(-1)
            STOP -> finish()
            else -> if (QuranAudio.state.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun publish(loading: Boolean = false) {
        QuranAudio.state.value = Recitation(reciter.id, s, a, playing = !paused, loading = loading)
        notifyNow()
    }

    private fun start() {
        job?.cancel()
        release()
        requestFocus()
        job = scope.launch {
            while (isActive) {
                // the basmala before a surah's first verse
                if (a == 1 && s != 1 && s != 9) {
                    publish(loading = true)
                    if (!playVerse(1, 1)) return@launch
                }
                publish(loading = true)
                if (!playVerse(s, a)) return@launch
                // on to the next verse, and into the next surah
                if (a < Reciters.ayahs[s - 1]) a++ else if (s < 114) { s++; a = 1 } else { finish(); return@launch }
            }
        }
    }

    /** Plays one verse to its end; false when it could not be fetched (the playing stops). */
    private suspend fun playVerse(vs: Int, va: Int): Boolean {
        val f = withContext(Dispatchers.IO) { fetch(vs, va) }
        if (f == null) {
            QuranAudio.error.value = t("تعذّر تحميل التلاوة، تأكد من الاتصال بالإنترنت")
            finish()
            return false
        }
        // the next verses are fetched while this one plays
        scope.launch(Dispatchers.IO) { ahead(vs, va) }
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val ok = runCatching { mp.setDataSource(f.path); mp.prepare() }.isSuccess
        if (!ok) { f.delete(); mp.release(); player = null; return true }
        publish(loading = false)
        if (!paused) mp.start()
        suspendCancellableCoroutine { cont ->
            mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
            mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
            cont.invokeOnCancellation { }
        }
        runCatching { mp.release() }
        if (player === mp) player = null
        return true
    }

    private fun fetch(vs: Int, va: Int): File? {
        val f = QuranAudio.file(this, reciter, vs, va)
        if (f.exists() && f.length() > 0) return f
        return runCatching {
            f.parentFile?.mkdirs()
            val part = File(f.path + ".part")
            val c = URL("https://everyayah.com/data/${reciter.folder}/%03d%03d.mp3".format(vs, va)).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000
            c.inputStream.use { inp -> part.outputStream().use { inp.copyTo(it) } }
            if (c.responseCode != 200) { part.delete(); return null }
            part.renameTo(f)
            f
        }.getOrNull()
    }

    private fun ahead(vs: Int, va: Int) {
        var ss = vs; var aa = va
        repeat(3) {
            if (aa < Reciters.ayahs[ss - 1]) aa++ else if (ss < 114) { ss++; aa = 1 } else return
            fetch(ss, aa)
        }
    }

    private fun pause() {
        paused = true
        runCatching { player?.pause() }
        publish()
    }

    private fun resume() {
        paused = false
        requestFocus()
        runCatching { player?.start() }
        publish()
    }

    private fun skip(by: Int) {
        if (QuranAudio.state.value == null) { stopSelf(); return }
        if (by > 0) { if (a < Reciters.ayahs[s - 1]) a++ else if (s < 114) { s++; a = 1 } }
        else { if (a > 1) a-- else if (s > 1) { s--; a = Reciters.ayahs[s - 1] } }
        paused = false
        start()
    }

    private fun finish() {
        job?.cancel(); job = null
        release()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        QuranAudio.state.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        runCatching { player?.release() }
        player = null
    }

    /** Another sound (a call, a video) pauses the recitation. */
    private fun requestFocus() {
        if (Build.VERSION.SDK_INT < 26) return
        val am = getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { if (!paused) pause() }
            }.build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun pending(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, QuranPlayerService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun build(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t("تلاوة القرآن"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        val open = PendingIntent.getActivity(this, 74, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val surah = com.kouwis.alzkr.data.Quran.peek()?.surahName(s) ?: ""
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ArabicText.digitsOf(t("{0} · الآية {1}", surah, a.toString())))
            .setContentText("${reciter.name} · ${reciter.style}")
            .setOngoing(!paused).setOnlyAlertOnce(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, t("السابقة"), pending(PREV, 1))
            .addAction(0, if (paused) t("تشغيل") else t("إيقاف مؤقت"), pending(TOGGLE, 2))
            .addAction(0, t("التالية"), pending(NEXT, 3))
            .addAction(0, t("إغلاق"), pending(STOP, 4))
            .build()
    }

    private fun foreground() {
        ServiceCompat.startForeground(this, ID, build(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
    }

    private fun notifyNow() {
        if (QuranAudio.state.value == null) return
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, build()) }
    }

    override fun onDestroy() {
        job?.cancel()
        release()
        QuranAudio.state.value = null
        scope.cancel()
        super.onDestroy()
    }
}
