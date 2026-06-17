package id.go.tabalong.inspektorat.notara.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Merekam audio mikrofon ke berkas AAC dalam kontainer MP4 (.m4a).
 * Inilah berkas .aac/AAC yang menjadi dasar pertimbangan untuk diolah.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    fun start(): File {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rekaman_${System.currentTimeMillis()}.m4a")

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()

        recorder = r
        outputFile = file
        return file
    }

    /** Amplitudo sesaat (0..32767) untuk visual gelombang. */
    fun amplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (e: Exception) {
        0
    }

    /** Hentikan & lepaskan. Mengembalikan true bila berkas valid tersimpan. */
    fun stop(): Boolean {
        return try {
            recorder?.stop()
            true
        } catch (e: Exception) {
            // Rekaman terlalu pendek / gagal: buang berkas
            outputFile?.delete()
            false
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
