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
data class Recitation(
    val reciter: String, val s: Int, val a: Int, val playing: Boolean, val loading: Boolean,
    /** the verse's turn of [PlayOptions.ayahRepeat], and the range's of [PlayOptions.rangeRepeat] (from 1) */
    val ayahTurn: Int = 1, val rangeTurn: Int = 1,
    /** playing the chosen range */
    val range: Boolean = false,
)

/**
 * How to listen, kept for next time: the speed (0.5 to 2), each verse said [ayahRepeat] times (0: always), and the
 * range from [fromS]:[fromA] to [toS]:[toA] (0: no end, carry on) said [rangeRepeat] times (0: always).
 */
data class PlayOptions(
    val speed: Float = 1f, val ayahRepeat: Int = 1, val rangeRepeat: Int = 1,
    val fromS: Int = 0, val fromA: Int = 0, val toS: Int = 0, val toA: Int = 0,
) {
    val hasRange get() = toS > 0
    companion object {
        private fun sp(c: Context) = c.getSharedPreferences("quran_audio", Context.MODE_PRIVATE)
        fun load(c: Context) = sp(c).let {
            PlayOptions(it.getFloat("speed", 1f), it.getInt("ayahRepeat", 1), it.getInt("rangeRepeat", 1),
                it.getInt("fromS", 0), it.getInt("fromA", 0), it.getInt("toS", 0), it.getInt("toA", 0))
        }
        fun save(c: Context, o: PlayOptions) = sp(c).edit().putFloat("speed", o.speed).putInt("ayahRepeat", o.ayahRepeat)
            .putInt("rangeRepeat", o.rangeRepeat).putInt("fromS", o.fromS).putInt("fromA", o.fromA)
            .putInt("toS", o.toS).putInt("toA", o.toA).apply()
    }
}

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

    private fun send(context: Context, action: String, s: Int = 0, a: Int = 0, range: Boolean = false) {
        val i = Intent(context, QuranPlayerService::class.java).setAction(action).putExtra("s", s).putExtra("a", a).putExtra("range", range)
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

    /** New options: kept, and taken up at once by what is playing (the speed straight away). */
    fun setOptions(context: Context, o: PlayOptions) {
        PlayOptions.save(context, o)
        options.value = o
        if (state.value != null) send(context, QuranPlayerService.OPTIONS)
    }
    val options = MutableStateFlow(PlayOptions())
    fun loadOptions(context: Context) { options.value = PlayOptions.load(context) }

    /** Plays the chosen range from its start. */
    fun playRange(context: Context) {
        val o = options.value
        if (o.fromS > 0) send(context, QuranPlayerService.PLAY, o.fromS, o.fromA.coerceAtLeast(1), range = true)
    }

    /** Another reciter: chosen for good, and the verse playing now starts again in his voice. */
    fun choose(context: Context, id: String) {
        Reciters.choose(context, id)
        reciter.value = id
        // playing: the same verse again at once in the new voice, keeping the passage and its repeats
        if (state.value != null) send(context, QuranPlayerService.RECITER)
    }
    /** the chosen reciter's id, for the screens to follow */
    val reciter = MutableStateFlow<String?>(null)
    fun current(context: Context): Reciter = Reciters.byId(reciter.value ?: Reciters.current(context).id)

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
        const val OPTIONS = "com.kouwis.alzkr.quran.OPTIONS"
        const val RECITER = "com.kouwis.alzkr.quran.RECITER"
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
    private var opts = PlayOptions()
    private var ayahTurn = 1
    private var rangeTurn = 1
    /** started with «تشغيل المقطع»: the chosen range applies (otherwise playing carries on) */
    private var rangeOn = false
    private fun withRange(o: PlayOptions) = if (rangeOn) o else o.copy(toS = 0, toA = 0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                reciter = Reciters.current(this)
                rangeOn = intent.getBooleanExtra("range", false)
                opts = withRange(PlayOptions.load(this)); QuranAudio.options.value = PlayOptions.load(this)
                rangeTurn = 1
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
            RECITER -> if (QuranAudio.state.value != null) {
                reciter = Reciters.current(this)
                paused = false
                ayahTurn = 1
                start()
            }
            OPTIONS -> {
                opts = withRange(PlayOptions.load(this))
                applySpeed(player)
                publish()
            }
            else -> if (QuranAudio.state.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun publish(loading: Boolean = false) {
        QuranAudio.state.value = Recitation(reciter.id, s, a, playing = !paused, loading = loading, ayahTurn = ayahTurn, rangeTurn = rangeTurn, range = rangeOn)
        runCatching { updateSession(loading) }
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
                    ayahTurn = 1
                    publish(loading = true)
                    if (!playVerse(1, 1)) return@launch
                }
                // the verse, as many times as chosen
                ayahTurn = 1
                while (true) {
                    publish(loading = true)
                    if (!playVerse(s, a)) return@launch
                    // 0: the verse again and again, until stopped or skipped
                    if (opts.ayahRepeat != 0 && ayahTurn >= opts.ayahRepeat) break
                    ayahTurn++
                }
                ayahTurn = 1
                // the end of the chosen range: again from its start, or done
                if (opts.hasRange && s == opts.toS && a == opts.toA) {
                    if (opts.rangeRepeat == 0 || rangeTurn < opts.rangeRepeat) {
                        rangeTurn++
                        s = opts.fromS.coerceIn(1, 114); a = opts.fromA.coerceIn(1, Reciters.ayahs[s - 1])
                        continue
                    }
                    finish(); return@launch
                }
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
        applySpeed(mp)
        if (!paused) mp.start() else runCatching { mp.pause() }
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

    /** The chosen speed (Android 6 and later), keeping the reciter's pitch. */
    private fun applySpeed(mp: MediaPlayer?) {
        if (mp == null || Build.VERSION.SDK_INT < 23) return
        runCatching {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = mp.playbackParams.setSpeed(opts.speed.coerceIn(0.5f, 2f)).setPitch(1f)
            // setting the speed starts a paused player: keep it as it was
            if (!wasPlaying && paused) mp.pause()
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
        session?.run { isActive = false; release() }; session = null
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

    /*
     * The phone's media controls (the player in the notifications and on the lock screen, like music):
     * a media session with the verse, the reciter and the buttons previous / play-pause / next / close.
     */
    private var session: android.media.session.MediaSession? = null
    private val art by lazy {
        runCatching {
            val d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_fg)!!
            val bg = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_bg)!!
            val bmp = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            bg.setBounds(0, 0, 512, 512); bg.draw(cv)
            d.setBounds(0, 0, 512, 512); d.draw(cv)
            bmp
        }.getOrNull()
    }

    private fun session(): android.media.session.MediaSession = session ?: android.media.session.MediaSession(this, "quran").apply {
        setCallback(object : android.media.session.MediaSession.Callback() {
            override fun onPlay() { if (paused) resume() }
            override fun onPause() { if (!paused) pause() }
            override fun onSkipToNext() = skip(+1)
            override fun onSkipToPrevious() = skip(-1)
            override fun onStop() = finish()
            override fun onCustomAction(action: String, extras: android.os.Bundle?) { if (action == "close") finish() }
        })
        setSessionActivity(PendingIntent.getActivity(this@QuranPlayerService, 74, Intent(this@QuranPlayerService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
        isActive = true
        session = this
    }

    private fun updateSession(loading: Boolean) {
        val ss = session()
        val surah = com.kouwis.alzkr.data.Quran.peek()?.surahName(s) ?: ""
        val title = ArabicText.digitsOf(t("{0} · الآية {1}", surah, a.toString()))
        ss.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "${reciter.name} · ${reciter.style}")
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, t("القرآن الكريم"))
                .apply { art?.let { putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, it); putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        val st = when {
            loading && !paused -> android.media.session.PlaybackState.STATE_BUFFERING
            paused -> android.media.session.PlaybackState.STATE_PAUSED
            else -> android.media.session.PlaybackState.STATE_PLAYING
        }
        ss.setPlaybackState(
            android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY or android.media.session.PlaybackState.ACTION_PAUSE or
                        android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or android.media.session.PlaybackState.ACTION_STOP,
                )
                .addCustomAction(android.media.session.PlaybackState.CustomAction.Builder("close", t("إغلاق"), R.drawable.ic_media_close).build())
                .setState(st, android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN, opts.speed)
                .build(),
        )
    }

    private fun action(icon: Int, label: String, act: String, code: Int) =
        android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, pending(act, code)).build()

    private fun build(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t("تلاوة القرآن"), NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        val ss = session()
        val surah = com.kouwis.alzkr.data.Quran.peek()?.surahName(s) ?: ""
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ArabicText.digitsOf(t("{0} · الآية {1}", surah, a.toString())))
            .setContentText("${reciter.name} · ${reciter.style}")
            .apply { art?.let { setLargeIcon(it) } }
            .setOngoing(!paused).setOnlyAlertOnce(true)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setContentIntent(ss.controller.sessionActivity)
            .setDeleteIntent(pending(STOP, 5))
            .addAction(action(R.drawable.ic_media_prev, t("السابقة"), PREV, 1))
            .addAction(action(if (paused) R.drawable.ic_media_play else R.drawable.ic_media_pause, if (paused) t("تشغيل") else t("إيقاف مؤقت"), TOGGLE, 2))
            .addAction(action(R.drawable.ic_media_next, t("التالية"), NEXT, 3))
            .addAction(action(R.drawable.ic_media_close, t("إغلاق"), STOP, 4))
            .setStyle(android.app.Notification.MediaStyle().setMediaSession(ss.sessionToken).setShowActionsInCompactView(0, 1, 2))
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
        session?.run { isActive = false; release() }; session = null
        scope.cancel()
        super.onDestroy()
    }
}
