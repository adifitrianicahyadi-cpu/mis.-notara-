package id.go.tabalong.inspektorat.notara.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import id.go.tabalong.inspektorat.notara.MainActivity
import id.go.tabalong.inspektorat.notara.R
import id.go.tabalong.inspektorat.notara.data.NotaraDatabase
import id.go.tabalong.inspektorat.notara.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

/** State perekaman yang diamati oleh UI. */
data class RecState(
    val recording: Boolean = false,
    val elapsedSec: Int = 0,
    val amp: Float = 0f,
    val filePath: String? = null,
    val finished: Boolean = false,
    val savedNoteId: String? = null,
    val error: String? = null
)

/**
 * Foreground service tipe "microphone": perekaman tetap aktif saat layar terkunci
 * atau aplikasi di latar belakang. Hasil disimpan langsung ke database agar tidak
 * hilang walau dihentikan dari notifikasi.
 */
class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = LocalBinder()
    private var recorder: AudioRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var startTime = 0L
    private var lastNotifSec = -1

    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP_SAVE -> stop(save = true)
            ACTION_STOP_DISCARD -> stop(save = false)
        }
        return START_NOT_STICKY
    }

    fun start() {
        if (_state.value.recording) return
        createChannel()
        val rec = AudioRecorder(this)
        val file = try {
            rec.start()
        } catch (e: Exception) {
            _state.value = RecState(error = "Gagal memulai perekaman (izin mikrofon?)")
            stopSelf()
            return
        }
        recorder = rec
        startTime = System.currentTimeMillis()
        lastNotifSec = -1

        // Jadikan foreground SEBELUM proses panjang (wajib < 5 detik setelah startForegroundService).
        startAsForeground(0)
        acquireWake()

        _state.value = RecState(recording = true, elapsedSec = 0, amp = 0f, filePath = file.absolutePath)

        ticker = scope.launch {
            while (isActive && _state.value.recording) {
                val el = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val amp = min(1f, (recorder?.amplitude() ?: 0) / 20000f)
                _state.value = _state.value.copy(elapsedSec = el, amp = amp)
                if (el != lastNotifSec) {
                    lastNotifSec = el
                    notify(el)
                }
                delay(150)
            }
        }
    }

    fun stop(save: Boolean): String? {
        if (!_state.value.recording) return null
        val ok = recorder?.stop() ?: false
        val path = _state.value.filePath
        val elapsed = _state.value.elapsedSec
        ticker?.cancel()
        releaseWake()
        recorder = null
        _state.value = _state.value.copy(recording = false)

        if (save && ok && path != null) {
            val note = buildNote(path, elapsed)
            scope.launch {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    NotaraDatabase.get(applicationContext).noteDao().upsert(note)
                }
                _state.value = RecState(recording = false, finished = true, savedNoteId = note.id)
                finishForeground()
            }
        } else {
            if (path != null) runCatching { File(path).delete() }
            _state.value = RecState(recording = false, finished = true, savedNoteId = null)
            finishForeground()
        }
        return path
    }

    private fun buildNote(path: String, elapsedSec: Int): Note {
        val id = "n" + UUID.randomUUID().toString().take(10)
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(now))
        return Note(
            id = id,
            type = "record",
            title = "Rekaman $stamp",
            createdAt = now,
            durationSec = elapsedSec,
            audioPath = path,
            fileName = File(path).name,
            mimeType = "audio/aac"
        )
    }

    private fun finishForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    // ---------- Notifikasi ----------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Perekaman Audio", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifikasi saat NOTARA sedang merekam"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(elapsedSec: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags()
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP_SAVE },
            pendingFlags()
        )
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOTARA — merekam")
            .setContentText("Durasi %02d:%02d • berjalan walau layar terkunci".format(mm, ss))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "Hentikan & simpan", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startAsForeground(elapsedSec: Int) {
        val n = buildNotification(elapsedSec)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notify(elapsedSec: Int) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(elapsedSec))
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT

    // ---------- Wake lock ----------
    private fun acquireWake() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "notara:recording").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L) // batas aman 3 jam
        }
    }

    private fun releaseWake() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onDestroy() {
        ticker?.cancel()
        releaseWake()
        try { recorder?.stop() } catch (_: Exception) {}
        recorder = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "notara_recording"
        const val NOTIF_ID = 1001
        const val ACTION_START = "id.go.tabalong.inspektorat.notara.START"
        const val ACTION_STOP_SAVE = "id.go.tabalong.inspektorat.notara.STOP_SAVE"
        const val ACTION_STOP_DISCARD = "id.go.tabalong.inspektorat.notara.STOP_DISCARD"
    }
}
